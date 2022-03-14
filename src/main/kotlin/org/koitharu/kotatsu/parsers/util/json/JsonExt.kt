package org.koitharu.kotatsu.parsers.util.json

import androidx.collection.ArraySet
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.utils.json.JSONIterator
import org.koitharu.kotatsu.utils.json.JSONStringIterator
import org.koitharu.kotatsu.utils.json.JSONValuesIterator
import kotlin.contracts.contract

inline fun <R, C : MutableCollection<in R>> JSONArray.mapJSONTo(
	destination: C,
	block: (JSONObject) -> R,
): C {
	val len = length()
	for (i in 0 until len) {
		val jo = getJSONObject(i)
		destination.add(block(jo))
	}
	return destination
}

inline fun <R, C : MutableCollection<in R>> JSONArray.mapJSONNotNullTo(
	destination: C,
	block: (JSONObject) -> R?,
): C {
	val len = length()
	for (i in 0 until len) {
		val jo = getJSONObject(i)
		destination.add(block(jo) ?: continue)
	}
	return destination
}

inline fun <T> JSONArray.mapJSON(block: (JSONObject) -> T): List<T> {
	return mapJSONTo(ArrayList(length()), block)
}

inline fun <T> JSONArray.mapJSONNotNull(block: (JSONObject) -> T?): List<T> {
	return mapJSONNotNullTo(ArrayList(length()), block)
}

fun <T> JSONArray.mapJSONIndexed(block: (Int, JSONObject) -> T): List<T> {
	val len = length()
	val result = ArrayList<T>(len)
	for (i in 0 until len) {
		val jo = getJSONObject(i)
		result.add(block(i, jo))
	}
	return result
}

fun JSONObject.getStringOrNull(name: String): String? = opt(name)?.takeUnless {
	it === JSONObject.NULL
}?.toString()?.takeUnless {
	it.isEmpty()
}

fun JSONObject.getBooleanOrDefault(name: String, defaultValue: Boolean): Boolean = opt(name)?.takeUnless {
	it === JSONObject.NULL
} as? Boolean ?: defaultValue

fun JSONObject.getLongOrDefault(name: String, defaultValue: Long): Long = opt(name)?.takeUnless {
	it === JSONObject.NULL
} as? Long ?: defaultValue

fun JSONArray.JSONIterator(): Iterator<JSONObject> = JSONIterator(this)

fun JSONArray.stringIterator(): Iterator<String> = JSONStringIterator(this)

fun <T> JSONArray.mapJSONToSet(block: (JSONObject) -> T): Set<T> {
	val len = length()
	val result = ArraySet<T>(len)
	for (i in 0 until len) {
		val jo = getJSONObject(i)
		result.add(block(jo))
	}
	return result
}

fun JSONObject.values(): Iterator<Any> = JSONValuesIterator(this)

fun JSONArray.associateByKey(key: String): Map<String, JSONObject> {
	val destination = LinkedHashMap<String, JSONObject>(length())
	repeat(length()) { i ->
		val item = getJSONObject(i)
		val keyValue = item.getString(key)
		destination[keyValue] = item
	}
	return destination
}

fun JSONArray?.isNullOrEmpty(): Boolean {
	contract {
		returns(false) implies (this@isNullOrEmpty != null)
	}

	return this == null || this.length() == 0
}