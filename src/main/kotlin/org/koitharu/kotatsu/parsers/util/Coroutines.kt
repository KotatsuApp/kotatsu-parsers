package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.selects.select
import kotlin.coroutines.cancellation.CancellationException

fun Iterable<Job>.cancelAll(cause: CancellationException? = null) {
	forEach { it.cancel(cause) }
}

suspend fun <T> Iterable<Deferred<T>>.awaitFirst(): T = select<T> {
	for (async in this@awaitFirst) {
		async.onAwait { it }
	}
}.also { this@awaitFirst.cancelAll() }

suspend fun <T> Collection<Deferred<T>>.awaitFirst(condition: (T) -> Boolean): T {
	var result: Any? = NULL
	var counter = size
	while (result === NULL && counter > 0) {
		val candidate = select<T> {
			for (async in this@awaitFirst) {
				async.onAwait { it }
			}
		}
		if (condition(candidate)) {
			result = candidate
		}
		counter--
	}
	cancelAll()
	if (result === NULL) {
		throw NoSuchElementException()
	}
	@Suppress("UNCHECKED_CAST")
	return result as T
}

private val NULL = Any()
