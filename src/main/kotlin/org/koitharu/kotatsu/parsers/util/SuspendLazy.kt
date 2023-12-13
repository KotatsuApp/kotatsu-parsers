package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SuspendLazy<T>(
	private val initializer: suspend () -> T,
) {

	private val mutex = Mutex()
	private var cachedValue: Any? = Uninitialized

	@Suppress("UNCHECKED_CAST")
	suspend fun get(): T {
		// fast way
		cachedValue.let {
			if (it !== Uninitialized) {
				return it as T
			}
		}
		return mutex.withLock {
			cachedValue.let {
				if (it !== Uninitialized) {
					return it as T
				}
			}
			val result = initializer()
			cachedValue = result
			result
		}
	}

	suspend fun tryGet() = runCatchingCancellable { get() }

	@Suppress("UNCHECKED_CAST")
	fun peek(): T? {
		return cachedValue?.takeUnless { it === Uninitialized } as T?
	}

	private object Uninitialized
}
