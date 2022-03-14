package org.koitharu.kotatsu.parsers

import okhttp3.Interceptor
import okhttp3.Response

private const val HEADER_USER_AGENT = "User-Agent"
internal class UserAgentInterceptor(
	private val userAgent: String,
) : Interceptor {

	override fun intercept(chain: Interceptor.Chain): Response {
		val request = chain.request()
		val newRequest = if (request.header(HEADER_USER_AGENT) == null) {
			request.newBuilder().header(HEADER_USER_AGENT, userAgent).build()
		} else {
			request
		}
		return chain.proceed(newRequest)
	}
}