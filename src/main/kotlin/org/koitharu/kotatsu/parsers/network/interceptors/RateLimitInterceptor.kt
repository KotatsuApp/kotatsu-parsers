package org.koitharu.kotatsu.parsers.network.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.notifyAll
import okio.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

// TODO rewrite this
class RateLimitInterceptor : Interceptor {

	private val requestQueue = ArrayDeque<Long>(10)
	private val rateLimitMillis = TimeUnit.SECONDS.toMillis(60L)
	private val fairLock = Semaphore(1, true)

	override fun intercept(chain: Interceptor.Chain): Response {
		val call = chain.call()
		val request = chain.request()

		try {
			fairLock.acquire()
		} catch (e: InterruptedException) {
			throw IOException(e)
		}

		val requestQueue = this.requestQueue
		val timestamp: Long

		try {
			synchronized(requestQueue) {
				while (requestQueue.size >= 10) {
					val periodStart = System.currentTimeMillis() - rateLimitMillis
					var hasRemovedExpired = false
					while (requestQueue.isEmpty().not() && requestQueue.first() <= periodStart) {
						requestQueue.removeFirst()
						hasRemovedExpired = true
					}
					if (call.isCanceled()) {
						throw IOException("Canceled")
					} else if (hasRemovedExpired) {
						break
					} else {
						try {
							requestQueue.wait(requestQueue.first() - periodStart)
						} catch (_: InterruptedException) {
							continue
						}
					}
				}

				timestamp = System.currentTimeMillis()
				requestQueue.addLast(timestamp)
			}
		} finally {
			fairLock.release()
		}

		val response = chain.proceed(request)
		if (response.networkResponse == null) {
			synchronized(requestQueue) {
				if (requestQueue.isEmpty() || timestamp < requestQueue.first()) return@synchronized
				val iterator = requestQueue.iterator()
				while (iterator.hasNext()) {
					if (iterator.next() == timestamp) {
						iterator.remove()
						break
					}
				}
				requestQueue.notifyAll()
			}
		}

		return response
	}

	@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "NOTHING_TO_INLINE")
	private inline fun Any.wait(timeout: Long) = (this as Object).wait(timeout)
}
