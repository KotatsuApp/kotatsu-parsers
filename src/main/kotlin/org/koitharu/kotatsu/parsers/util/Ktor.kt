@file:JvmName("KtorUtils")

package org.koitharu.kotatsu.parsers.util

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.io.bytestring.decodeToString
import org.koitharu.kotatsu.parsers.network.HttpInterceptor
import org.koitharu.kotatsu.parsers.network.HttpInterceptorAdapter
import java.net.URI

public fun HttpClient.interceptor(interceptor: HttpInterceptor) {
	plugin(HttpSend).intercept(HttpInterceptorAdapter(interceptor))
}

@Suppress("NOTHING_TO_INLINE")
@Deprecated("", replaceWith = ReplaceWith("Url(this)"))
public inline fun String.toUrl() = Url(this)

@Suppress("NOTHING_TO_INLINE")
@Deprecated("", replaceWith = ReplaceWith("parseUrl(this)"))
public inline fun String.toUrlOrNull() = parseUrl(this)

public suspend fun HttpResponse.peekBodyString(length: Int): String =
	bodyAsChannel().peek(length)?.decodeToString().orEmpty()

public inline fun Headers.withBuilder(block: HeadersBuilder.() -> Unit): Headers = HeadersBuilder().apply {
	appendAll(this@withBuilder)
	block()
}.build()
