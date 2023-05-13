package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.CancellationException

inline fun <T, R> T.runCatchingCancellable(block: T.() -> R): Result<R> {
	return try {
		Result.success(block())
	} catch (e: CancellationException) {
		throw e
	} catch (e: Throwable) {
		Result.failure(e)
	}
}