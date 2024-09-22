package org.koitharu.kotatsu.parsers.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.SoftReference

/**
 * Like a [SuspendLazy] but with [SoftReference] under the hood
 */
public class SoftSuspendLazy<T : Any>(
	private val initializer: suspend () -> T,
) {

	private val mutex = Mutex()
	private var cachedValue: SoftReference<T>? = null

	public suspend fun get(): T {
		// fast way
		cachedValue?.get()?.let {
			return it
		}
		return mutex.withLock {
			cachedValue?.get()?.let {
				return it
			}
			val result = initializer()
			cachedValue = SoftReference(result)
			result
		}
	}

	public suspend fun tryGet(): Result<T> = runCatchingCancellable { get() }

	public fun peek(): T? {
		return cachedValue?.get()
	}
}
