@file:JvmName("StringUtils")

package org.koitharu.kotatsu.parsers.util

import androidx.collection.MutableIntList
import androidx.collection.arraySetOf
import java.math.BigInteger
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.*
import kotlin.math.min

private val REGEX_WHITESPACE = Regex("\\s+")

public fun String.removeSurrounding(vararg chars: Char): String {
	if (isEmpty()) {
		return this
	}
	for (c in chars) {
		if (first() == c && last() == c) {
			return substring(1, length - 1)
		}
	}
	return this
}

public fun String.toCamelCase(): String {
	if (isEmpty()) {
		return this
	}
	val result = StringBuilder(length)
	var capitalize = true
	for (char in this) {
		result.append(
			if (capitalize) {
				char.uppercase()
			} else {
				char.lowercase()
			},
		)
		capitalize = char.isWhitespace()
	}
	return result.toString()
}

public fun String.toTitleCase(): String {
	return replaceFirstChar { x -> x.uppercase() }
}

public fun String.toTitleCase(locale: Locale): String {
	return replaceFirstChar { x -> x.uppercase(locale) }
}

public fun String.transliterate(skipMissing: Boolean): String {
	val cyr = charArrayOf(
		'а', 'б', 'в', 'г', 'д', 'е', 'ж', 'з', 'и', 'й', 'к', 'л', 'м', 'н', 'о', 'п',
		'р', 'с', 'т', 'у', 'ф', 'х', 'ц', 'ч', 'ш', 'щ', 'ъ', 'ы', 'ь', 'э', 'ю', 'я', 'ё', 'ў',
	)
	val lat = arrayOf(
		"a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p",
		"r", "s", "t", "u", "f", "h", "ts", "ch", "sh", "sch", "", "i", "", "e", "ju", "ja", "jo", "w",
	)
	return buildString(length + 5) {
		for (c in this@transliterate) {
			val p = cyr.binarySearch(c.lowercaseChar())
			if (p in lat.indices) {
				if (c.isUpperCase()) {
					append(lat[p].uppercase())
				} else {
					append(lat[p])
				}
			} else if (!skipMissing) {
				append(c)
			}
		}
	}
}

public fun String.toFileNameSafe(): String = this.transliterate(false)
	.replace(Regex("[^a-z0-9_\\-]", arraySetOf(RegexOption.IGNORE_CASE)), " ")
	.replace(REGEX_WHITESPACE, "_")

public fun String.ellipsize(maxLength: Int): String = if (this.length > maxLength) {
	this.take(maxLength - 1) + Typography.ellipsis
} else this

public fun String.splitTwoParts(delimiter: Char): Pair<String, String>? {
	val indices = MutableIntList(4)
	for ((i, c) in this.withIndex()) {
		if (c == delimiter) {
			indices += i
		}
	}
	if (indices.isEmpty() || indices.size and 1 == 0) {
		return null
	}
	val index = indices[indices.size / 2]
	return substring(0, index) to substring(index + 1)
}

public fun String.urlEncoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

public fun String.urlDecode(): String = URLDecoder.decode(this, Charsets.UTF_8.name())

public fun String.nl2br(): String = replace("\n", "<br>")

public fun String.splitByWhitespace(): List<String> = trim().split(REGEX_WHITESPACE)

public fun ByteArray.byte2HexFormatted(): String {
	val str = StringBuilder(size * 2)
	for (i in indices) {
		var h = Integer.toHexString(this[i].toInt())
		val l = h.length
		if (l == 1) {
			h = "0$h"
		}
		if (l > 2) {
			h = h.substring(l - 2, l)
		}
		str.append(h.uppercase(Locale.ROOT))
		if (i < size - 1) {
			str.append(':')
		}
	}
	return str.toString()
}

public fun String.md5(): String {
	val md = MessageDigest.getInstance("MD5")
	return BigInteger(1, md.digest(toByteArray()))
		.toString(16)
		.padStart(32, '0')
}

public fun String.substringBetween(from: String, to: String, fallbackValue: String = this): String {
	val fromIndex = indexOf(from)
	if (fromIndex == -1) {
		return fallbackValue
	}
	val toIndex = lastIndexOf(to)
	return if (toIndex == -1) {
		fallbackValue
	} else {
		substring(fromIndex + from.length, toIndex)
	}
}

public fun String.substringBetweenFirst(from: String, to: String): String? {
	val fromIndex = indexOf(from)
	if (fromIndex == -1) {
		return null
	}
	val toIndex = indexOf(to, fromIndex)
	return if (toIndex == -1) {
		null
	} else {
		substring(fromIndex + from.length, toIndex)
	}
}

public fun String.substringBetweenLast(from: String, to: String, fallbackValue: String = this): String {
	val fromIndex = lastIndexOf(from)
	if (fromIndex == -1) {
		return fallbackValue
	}
	val toIndex = lastIndexOf(to)
	return if (toIndex == -1) {
		fallbackValue
	} else {
		substring(fromIndex + from.length, toIndex)
	}
}

public fun String.find(regex: Regex): String? = regex.find(this)?.value

public fun String.findGroupValue(regex: Regex): String? = regex.find(this)?.groupValues?.getOrNull(1)

public fun String.removeSuffix(suffix: Char): String {
	if (lastOrNull() == suffix) {
		return substring(0, length - 1)
	}
	return this
}

public fun String.levenshteinDistance(other: String): Int {
	if (this == other) {
		return 0
	}
	if (this.isEmpty()) {
		return other.length
	}
	if (other.isEmpty()) {
		return this.length
	}

	val lhsLength = this.length + 1
	val rhsLength = other.length + 1

	var cost = Array(lhsLength) { it }
	var newCost = Array(lhsLength) { 0 }

	for (i in 1 until rhsLength) {
		newCost[0] = i

		for (j in 1 until lhsLength) {
			val match = if (this[j - 1] == other[i - 1]) 0 else 1

			val costReplace = cost[j - 1] + match
			val costInsert = cost[j] + 1
			val costDelete = newCost[j - 1] + 1

			newCost[j] = min(min(costInsert, costDelete), costReplace)
		}

		val swap = cost
		cost = newCost
		newCost = swap
	}

	return cost[lhsLength - 1]
}

/**
 * @param threshold 0 = exact match
 */
public fun String.almostEquals(other: String, threshold: Float): Boolean {
	if (threshold <= 0f) {
		return equals(other, ignoreCase = true)
	}
	val diff = lowercase().levenshteinDistance(other.lowercase()) / ((length + other.length) / 2f)
	return diff < threshold
}

public fun String.isNumeric(): Boolean = all { c -> c.isDigit() }

internal fun StringBuilder.removeTrailingZero() {
	if (length > 2 && get(length - 1) == '0') {
		val dot = get(length - 2)
		if (dot == ',' || dot == '.') {
			delete(length - 2, length)
		}
	}
}
