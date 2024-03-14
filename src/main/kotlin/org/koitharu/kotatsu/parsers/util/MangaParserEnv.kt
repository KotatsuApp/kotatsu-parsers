package org.koitharu.kotatsu.parsers.util

import okhttp3.HttpUrl
import org.jsoup.nodes.Element
import org.koitharu.kotatsu.parsers.ErrorMessages
import org.koitharu.kotatsu.parsers.InternalParsersApi
import org.koitharu.kotatsu.parsers.MangaParser
import org.koitharu.kotatsu.parsers.exception.ParseException
import org.koitharu.kotatsu.parsers.model.*


/**
 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
 * @param url must be relative url, without a domain
 * @see [Manga.id]
 * @see [MangaChapter.id]
 * @see [MangaPage.id]
 */
@InternalParsersApi
fun MangaParser.generateUid(url: String): Long {
	var h = 1125899906842597L
	source.name.forEach { c ->
		h = 31 * h + c.code
	}
	url.forEach { c ->
		h = 31 * h + c.code
	}
	return h
}

/**
 * Create a unique id for [Manga]/[MangaChapter]/[MangaPage].
 * @param id an internal identifier
 * @see [Manga.id]
 * @see [MangaChapter.id]
 * @see [MangaPage.id]
 */
@InternalParsersApi
fun MangaParser.generateUid(id: Long): Long {
	var h = 1125899906842597L
	source.name.forEach { c ->
		h = 31 * h + c.code
	}
	h = 31 * h + id
	return h
}

@InternalParsersApi
fun Element.parseFailed(message: String? = null): Nothing {
	throw ParseException(message, ownerDocument()?.location() ?: baseUri(), null)
}

@InternalParsersApi
fun Set<MangaTag>?.oneOrThrowIfMany(): MangaTag? {
	return when {
		isNullOrEmpty() -> null
		size == 1 -> first()
		else -> throw IllegalArgumentException(ErrorMessages.FILTER_MULTIPLE_GENRES_NOT_SUPPORTED)
	}
}

@InternalParsersApi
fun Set<MangaState>?.oneOrThrowIfMany(): MangaState? {
	return when {
		isNullOrEmpty() -> null
		size == 1 -> first()
		else -> throw IllegalArgumentException(ErrorMessages.FILTER_MULTIPLE_STATES_NOT_SUPPORTED)
	}
}

@InternalParsersApi
fun Set<ContentRating>?.oneOrThrowIfMany(): ContentRating? {
	return when {
		isNullOrEmpty() -> null
		size == 1 -> first()
		else -> throw IllegalArgumentException(ErrorMessages.FILTER_MULTIPLE_CONTENT_RATING_NOT_SUPPORTED)
	}
}

val MangaParser.domain: String
	get() {
		return config[configKeyDomain]
	}

fun MangaParser.getDomain(subdomain: String): String {
	val domain = domain
	return subdomain + "." + domain.removePrefix("www.")
}

fun MangaParser.urlBuilder(subdomain: String? = null): HttpUrl.Builder {
	return HttpUrl.Builder()
		.scheme("https")
		.host(if (subdomain == null) domain else "$subdomain.$domain")
}
