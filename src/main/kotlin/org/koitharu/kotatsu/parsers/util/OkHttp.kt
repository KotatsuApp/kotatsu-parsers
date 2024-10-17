@file:JvmName("OkHttpUtils")

package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Response
import okhttp3.ResponseBody
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

public suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
	val callback = ContinuationCallCallback(this, continuation)
	enqueue(callback)
	continuation.invokeOnCancellation(callback)
}

public val Response.mimeType: String?
	get() = header("content-type")?.takeUnless { it.isEmpty() }

public val Response.contentDisposition: String?
	get() = header("Content-Disposition")

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
