package org.koitharu.kotatsu.parsers.model

class MangaTag(
	val title: String,
	val key: String,
	val source: MangaSource,
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MangaTag

		if (title != other.title) return false
		if (key != other.key) return false
		if (source != other.source) return false

		return true
	}

	override fun hashCode(): Int {
		var result = title.hashCode()
		result = 31 * result + key.hashCode()
		result = 31 * result + source.hashCode()
		return result
	}
}