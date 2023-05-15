package org.koitharu.kotatsu.parsers.util.json

import org.json.JSONArray

class JSONTypedIterator<T>(
	private val array: JSONArray,
	private val cls: Class<T>,
) : Iterator<T> {

	private val total = array.length()
	private var index = 0

	override fun hasNext() = index < total

	override fun next(): T {
		return cls.cast(array.get(index++))
	}
}
