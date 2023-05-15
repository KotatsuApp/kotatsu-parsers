package org.koitharu.kotatsu.parsers.util.json

import androidx.collection.ArraySet
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
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

fun JSONObject.getBooleanOrDefault(name: String, defaultValue: Boolean): Boolean {
	return when (val rawValue = opt(name)) {
		null, JSONObject.NULL -> defaultValue
		is Boolean -> rawValue
		is Number -> rawValue.toInt() != 0
		is String -> rawValue.lowercase(Locale.ROOT).toBooleanStrictOrNull() ?: defaultValue
		else -> defaultValue
	}
}

fun JSONObject.getLongOrDefault(name: String, defaultValue: Long): Long {
	return when (val rawValue = opt(name)) {
		null, JSONObject.NULL -> defaultValue
		is Long -> rawValue
		is Number -> rawValue.toLong()
		is String -> rawValue.toLongOrNull() ?: defaultValue
		else -> defaultValue
	}
}

fun JSONObject.getIntOrDefault(name: String, defaultValue: Int): Int {
	return when (val rawValue = opt(name)) {
		null, JSONObject.NULL -> defaultValue
		is Int -> rawValue
		is Number -> rawValue.toInt()
		is String -> rawValue.toIntOrNull() ?: defaultValue
		else -> defaultValue
	}
}

fun JSONObject.getDoubleOrDefault(name: String, defaultValue: Double): Double {
	return when (val rawValue = opt(name)) {
		null, JSONObject.NULL -> defaultValue
		is Double -> rawValue
		is Number -> rawValue.toDouble()
		is String -> rawValue.toDoubleOrNull() ?: defaultValue
		else -> defaultValue
	}
}

fun JSONObject.getFloatOrDefault(name: String, defaultValue: Float): Float {
	return when (val rawValue = opt(name)) {
		null, JSONObject.NULL -> defaultValue
		is Float -> rawValue
		is Number -> rawValue.toFloat()
		is String -> rawValue.toFloatOrNull() ?: defaultValue
		else -> defaultValue
	}
}

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

fun JSONArray.toJSONList(): List<JSONObject> {
	return List(length()) { i -> getJSONObject(i) }
}

inline fun <reified T> JSONArray.asIterable(): Iterable<T> = Iterable {
	JSONTypedIterator(this, T::class.java)
}
