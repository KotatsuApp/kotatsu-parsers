@file:JvmName("OkHttpUtils")

package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Headers
import okhttp3.Response

suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
	val callback = ContinuationCallCallback(this, continuation)
	enqueue(callback)
	continuation.invokeOnCancellation(callback)
}

val Response.mimeType: String?
	get() = header("content-type")?.takeUnless { it.isEmpty() }

val Response.contentDisposition: String?
	get() = header("Content-Disposition")

fun Headers.Builder.mergeWith(other: Headers, replaceExisting: Boolean): Headers.Builder {
	for ((name, value) in other) {
		if (replaceExisting || this[name] == null) {
			this[name] = value
		}
	}
	return this
}

fun Response.copy() = newBuilder()
	.body(peekBody(Long.MAX_VALUE))
	.build()

fun Response.Builder.setHeader(name: String, value: String?) = if (value == null) {
	removeHeader(name)
} else {
	header(name, value)
}
