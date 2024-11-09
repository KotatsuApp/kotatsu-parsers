@file:JvmName("CollectionUtils")

package org.koitharu.kotatsu.parsers.util

import androidx.collection.ArrayMap
import androidx.collection.ArraySet
import java.util.*

public fun <T> MutableCollection<T>.replaceWith(subject: Iterable<T>) {
	clear()
	addAll(subject)
}

public fun <T, C : MutableCollection<in T>> Iterable<Iterable<T>>.flattenTo(destination: C): C {
	for (element in this) {
		destination.addAll(element)
	}
	return destination
}

public fun <T> List<T>.medianOrNull(): T? = when {
	isEmpty() -> null
	else -> get((size / 2).coerceIn(indices))
}

public inline fun <T, R> Collection<T>.mapToSet(transform: (T) -> R): Set<R> {
	return mapTo(ArraySet(size), transform)
}

public inline fun <T, R : Any> Collection<T>.mapNotNullToSet(transform: (T) -> R?): Set<R> {
	val destination = ArraySet<R>(size)
	for (item in this) {
		destination.add(transform(item) ?: continue)
	}
	return destination
}

public inline fun <T, reified R> Array<T>.mapToArray(transform: (T) -> R): Array<R> = Array(size) { i ->
	transform(get(i))
}

public fun <K, V> List<Pair<K, V>>.toMutableMap(): MutableMap<K, V> = toMap(ArrayMap(size))

public fun <T> MutableList<T>.move(sourceIndex: Int, targetIndex: Int) {
	if (sourceIndex <= targetIndex) {
		Collections.rotate(subList(sourceIndex, targetIndex + 1), -1)
	} else {
		Collections.rotate(subList(targetIndex, sourceIndex + 1), 1)
	}
}

public fun <T> Iterator<T>.nextOrNull(): T? = if (hasNext()) next() else null

public inline fun <T, K, V> Collection<T>.associateGrouping(transform: (T) -> Pair<K, V>): Map<K, Set<V>> {
	val result = LinkedHashMap<K, MutableSet<V>>(size)
	for (item in this) {
		val (k, v) = transform(item)
		result.getOrPut(k) { LinkedHashSet() }.add(v)
	}
	return result
}

public fun <K> MutableMap<K, Int>.incrementAndGet(key: K): Int {
	var value = get(key) ?: 0
	value++
	put(key, value)
	return value
}
