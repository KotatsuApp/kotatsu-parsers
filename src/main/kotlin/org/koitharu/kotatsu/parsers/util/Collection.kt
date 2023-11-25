@file:JvmName("CollectionUtils")

package org.koitharu.kotatsu.parsers.util

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import java.util.*

fun <T> MutableCollection<T>.replaceWith(subject: Iterable<T>) {
	clear()
	addAll(subject)
}

fun <T, C : MutableCollection<in T>> Iterable<Iterable<T>>.flattenTo(destination: C): C {
	for (element in this) {
		destination.addAll(element)
	}
	return destination
}

fun <T> List<T>.medianOrNull(): T? = when {
	isEmpty() -> null
	else -> get((size / 2).coerceIn(indices))
}

inline fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
	return mapTo(ArraySet(size), transform)
}

inline fun <T, R> Collection<T>.mapNotNullToSet(transform: (T) -> R?): Set<R> {
	val destination = ArraySet<R>(size)
	for (item in this) {
		destination.add(transform(item) ?: continue)
	}
	return destination
}

inline fun <T, reified R> Array<T>.mapToArray(transform: (T) -> R): Array<R> = Array(size) { i ->
	transform(get(i))
}

fun <K, V> List<Pair<K, V>>.toMutableMap(): MutableMap<K, V> = toMap(ArrayMap(size))

fun <T> MutableList<T>.move(sourceIndex: Int, targetIndex: Int) {
	if (sourceIndex <= targetIndex) {
		Collections.rotate(subList(sourceIndex, targetIndex + 1), -1)
	} else {
		Collections.rotate(subList(targetIndex, sourceIndex + 1), 1)
	}
}

inline fun <T> List<T>.areItemsEquals(other: List<T>, equals: (T, T) -> Boolean): Boolean {
	if (size != other.size) {
		return false
	}
	for (i in indices) {
		val a = this[i]
		val b = other[i]
		if (!equals(a, b)) {
			return false
		}
	}
	return true
}

fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

inline fun <T, K, V> Collection<T>.associateGrouping(transform: (T) -> Pair<K, V>): Map<K, Set<V>> {
	val result = LinkedHashMap<K, MutableSet<V>>(size)
	for (item in this) {
		val (k, v) = transform(item)
		result.getOrPut(k) { LinkedHashSet() }.add(v)
	}
	return result
}

fun <K> MutableMap<K, Int>.incrementAndGet(key: K): Int {
	var value = get(key) ?: 0
	value++
	put(key, value)
	return value
}
