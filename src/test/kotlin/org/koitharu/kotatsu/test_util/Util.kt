package org.koitharu.kotatsu.test_util

import androidx.collection.ArraySet
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.util.toRelativeUrl

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

internal fun <T, K> Collection<T>.isDistinctByNotNull(selector: (T) -> K?): Boolean {
	val set = ArraySet<K>(size)
	for (item in this) {
		if (!set.add(selector(item) ?: continue)) {
			return false
		}
	}
	return true
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

fun mangaOf(source: MangaParserSource, url: String): Manga {
	val httpUrl = url.toHttpUrlOrNull()
	var id = 1125899906842597L
	source.name.forEach { c -> id = 31 * id + c.code }
	url.forEach { c -> id = 31 * id + c.code }
	return Manga(
		id = id,
		title = httpUrl?.pathSegments?.last() ?: url,
		altTitle = null,
		url = httpUrl?.let { url.toRelativeUrl(it.host) } ?: url,
		publicUrl = url,
		rating = RATING_UNKNOWN,
		isNsfw = false,
		coverUrl = "",
		tags = emptySet(),
		state = null,
		author = null,
		largeCoverUrl = null,
		description = null,
		chapters = null,
		source = source,
	)
}
