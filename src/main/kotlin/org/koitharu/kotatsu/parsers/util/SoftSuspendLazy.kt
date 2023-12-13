package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.SoftReference

/**
 * Like a [SuspendLazy] but with [SoftReference] under the hood
 */
class SoftSuspendLazy<T : Any>(
	private val initializer: suspend () -> T,
) {

	private val mutex = Mutex()
	private var cachedValue: SoftReference<T>? = null

	suspend fun get(): T {
		// fast way
		cachedValue?.get()?.let {
			return it
		}
		return mutex.withLock {
			cachedValue?.get()?.let {
				return it
			}
			val result = initializer()
			cachedValue = SoftReference(result)
			result
		}
	}

	suspend fun tryGet() = runCatchingCancellable { get() }

	fun peek(): T? {
		return cachedValue?.get()
	}
}
