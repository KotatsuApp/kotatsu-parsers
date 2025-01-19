@file:JvmName("OkHttpUtils")

package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
	val callback = ContinuationCallCallback(this, continuation)
	enqueue(callback)
	continuation.invokeOnCancellation(callback)
}

public val Response.mimeType: String?
	get() = header("content-type")?.substringBefore(';')?.trim()?.nullIfEmpty()?.lowercase()

public val HttpUrl.isHttpOrHttps: Boolean
	get() = scheme.equals("https", ignoreCase = true) || scheme.equals("http", ignoreCase = true)

public fun Headers.Builder.mergeWith(other: Headers, replaceExisting: Boolean): Headers.Builder {
	for ((name, value) in other) {
		if (replaceExisting || this[name] == null) {
			this[name] = value
		}
	}
	return this
}

public fun Response.copy(): Response = newBuilder()
	.body(peekBody(Long.MAX_VALUE))
	.build()

public fun Response.Builder.setHeader(name: String, value: String?): Response.Builder = if (value == null) {
	removeHeader(name)
} else {
	header(name, value)
}

public inline fun Response.map(mapper: (ResponseBody) -> ResponseBody): Response {
	contract {
		callsInPlace(mapper, InvocationKind.AT_MOST_ONCE)
	}
	return body?.use { responseBody ->
		newBuilder()
			.body(mapper(responseBody))
			.build()
	} ?: this
}

public fun Cookie.newBuilder(): Cookie.Builder = Cookie.Builder().also { c ->
	c.name(name)
	c.value(value)
	if (persistent) {
		c.expiresAt(expiresAt)
	}
	if (hostOnly) {
		c.hostOnlyDomain(domain)
	} else {
		c.domain(domain)
	}
	c.path(path)
	if (secure) {
		c.secure()
	}
	if (httpOnly) {
		c.httpOnly()
	}
}
