package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

public class SuspendLazy<T>(
	private val initializer: suspend () -> T,
) {

	private val mutex = Mutex()
	private var cachedValue: Any? = Uninitialized

	@Suppress("UNCHECKED_CAST")
	public suspend fun get(): T {
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

	public suspend fun tryGet(): Result<T> = runCatchingCancellable { get() }

	@Suppress("UNCHECKED_CAST")
	public fun peek(): T? {
		return cachedValue?.takeUnless { it === Uninitialized } as T?
	}

	private object Uninitialized
}
