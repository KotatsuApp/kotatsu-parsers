package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.MangaParser

class MangaPage(
	/**
	 * Unique identifier for manga
	 */
	val id: Long,
	/**
	 * Relative url to page (**without** a domain) or any other uri.
	 * Used principally in parsers.
	 * May contain link to image or html page.
	 * @see MangaParser.getPageUrl
	 */
	val url: String,
	/**
	 * Absolute link to the chapter or website home page.
	 * Used in Referer header
	 */
	val referer: String,
	/**
	 * Absolute url of the small page image if exists, null otherwise
	 */
	val preview: String?,
	val source: MangaSource,
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MangaPage

		if (id != other.id) return false
		if (url != other.url) return false
		if (referer != other.referer) return false
		if (preview != other.preview) return false
		if (source != other.source) return false

		return true
	}

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + url.hashCode()
		result = 31 * result + referer.hashCode()
		result = 31 * result + (preview?.hashCode() ?: 0)
		result = 31 * result + source.hashCode()
		return result
	}
}