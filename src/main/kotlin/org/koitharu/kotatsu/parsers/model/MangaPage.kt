package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.MangaParser

class MangaPage(
	/**
	 * Unique identifier for manga
	 */
	@JvmField val id: Long,
	/**
	 * Relative url to page (**without** a domain) or any other uri.
	 * Used principally in parsers.
	 * May contain link to image or html page.
	 * @see MangaParser.getPageUrl
	 */
	@JvmField val url: String,
	/**
	 * Absolute url of the small page image if exists, null otherwise
	 */
	@JvmField val preview: String?,
	@JvmField val source: MangaSource,
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MangaPage

		if (id != other.id) return false
		if (url != other.url) return false
		if (preview != other.preview) return false
		return source == other.source
	}

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + url.hashCode()
		result = 31 * result + (preview?.hashCode() ?: 0)
		result = 31 * result + source.hashCode()
		return result
	}

	override fun toString(): String {
		return "MangaPage($id [$url] - $source)"
	}
}
