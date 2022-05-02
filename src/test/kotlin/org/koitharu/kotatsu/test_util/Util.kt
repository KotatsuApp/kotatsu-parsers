package org.koitharu.kotatsu.test_util

import androidx.collection.ArraySet

private val PATTERN_URL_ABSOLUTE = Regex("^https?://[\\s\\S]+", setOf(RegexOption.IGNORE_CASE))
private val PATTERN_URL_RELATIVE = Regex("^/[\\s\\S]+", setOf(RegexOption.IGNORE_CASE))

internal fun <T> Collection<T>.isDistinct(): Boolean {
	val set = ArraySet<T>(size)
	for (item in this) {
		if (!set.add(item)) {
			return false
		}
	}
	return set.size == size
}

internal fun <T, K> Collection<T>.isDistinctBy(selector: (T) -> K): Boolean {
	val set = ArraySet<K>(size)
	for (item in this) {
		if (!set.add(selector(item))) {
			return false
		}
	}
	return set.size == size
}

internal fun String.isUrlRelative() = matches(PATTERN_URL_RELATIVE)
internal fun String.isUrlAbsolute() = matches(PATTERN_URL_ABSOLUTE)

internal inline fun <T, K> Collection<T>.maxDuplicates(selector: (T) -> K): K? {
	return groupBy(selector).maxByOrNull { it.value.size }?.key
}

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> List<T>.component6(): T = get(5)

@Suppress("NOTHING_TO_INLINE")
inline operator fun <T> List<T>.component7(): T = get(6)