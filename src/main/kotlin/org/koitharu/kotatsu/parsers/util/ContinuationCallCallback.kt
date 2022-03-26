package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletionHandler
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class ContinuationCallCallback(
	private val call: Call,
	private val continuation: CancellableContinuation<Response>,
) : Callback, CompletionHandler {

	override fun onResponse(call: Call, response: Response) {
		if (continuation.isActive) {
			continuation.resume(response)
		}
	}

	override fun onFailure(call: Call, e: IOException) {
		if (!call.isCanceled() && continuation.isActive) {
			continuation.resumeWithException(e)
		}
	}

	override fun invoke(cause: Throwable?) {
		runCatching {
			call.cancel()
		}.onFailure { e ->
			cause?.addSuppressed(e)
		}
	}
}