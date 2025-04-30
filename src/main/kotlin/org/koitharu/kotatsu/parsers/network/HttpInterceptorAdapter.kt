package org.koitharu.kotatsu.parsers.network

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*

internal class HttpInterceptorAdapter(
	private val delegate: HttpInterceptor,
) : HttpSendInterceptor {

	override suspend fun invoke(
		p1: Sender,
		p2: HttpRequestBuilder,
	): HttpClientCall = delegate.intercept(p1, p2)
}
