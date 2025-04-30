package org.koitharu.kotatsu.parsers.network

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*

public fun interface HttpInterceptor {

	public suspend fun intercept(sender: Sender, request: HttpRequestBuilder): HttpClientCall
}
