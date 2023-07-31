package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.CancellationException

inline fun <T, R> T.runCatchingCancellable(block: T.() -> R): Result<R> {
	return try {
		Result.success(block())
	} catch (e: InterruptedException) {
		throw e
	} catch (e: CancellationException) {
		throw e
	} catch (e: Throwable) {
		Result.failure(e)
	}
}

inline fun <R, T : R> Result<T>.recoverCatchingCancellable(transform: (exception: Throwable) -> R): Result<R> {
	return when (val exception = exceptionOrNull()) {
		null -> this
		else -> runCatchingCancellable { transform(exception) }
	}
}

inline fun <R : Any, T : R> Result<T>.recoverNotNull(transform: (exception: Throwable) -> R?): Result<R> {
	return when (val exception = exceptionOrNull()) {
		null -> this
		else -> transform(exception)?.let(Result.Companion::success) ?: this
	}
}
