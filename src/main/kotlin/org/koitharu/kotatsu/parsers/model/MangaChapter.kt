package org.koitharu.kotatsu.parsers.model

class MangaChapter(
	/**
	 * An unique id of chapter
	 */
	val id: Long,
	/**
	 * User-readable name of chapter
	 */
	val name: String,
	/**
	 * Chapter number starting from 1
	 */
	val number: Int,
	/**
	 * Relative url to chapter (**without** a domain) or any other uri.
	 * Used principally in parsers
	 */
	val url: String,
	/**
	 * User-readable name of scanlator (releaser) or null if unknown
	 */
	val scanlator: String?,
	/**
	 * Chapter upload date in milliseconds
	 */
	val uploadDate: Long,
	/**
	 * User-readable name of branch.
	 * A branch is a group of chapters that overlap (e.g. different languages)
	 */
	val branch: String?,
	val source: MangaSource,
) : Comparable<MangaChapter> {

	override fun compareTo(other: MangaChapter): Int {
		return number.compareTo(other.number)
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as MangaChapter

		if (id != other.id) return false
		if (name != other.name) return false
		if (number != other.number) return false
		if (url != other.url) return false
		if (scanlator != other.scanlator) return false
		if (uploadDate != other.uploadDate) return false
		if (branch != other.branch) return false
		if (source != other.source) return false

		return true
	}

	override fun hashCode(): Int {
		var result = id.hashCode()
		result = 31 * result + name.hashCode()
		result = 31 * result + number
		result = 31 * result + url.hashCode()
		result = 31 * result + (scanlator?.hashCode() ?: 0)
		result = 31 * result + uploadDate.hashCode()
		result = 31 * result + (branch?.hashCode() ?: 0)
		result = 31 * result + source.hashCode()
		return result
	}
}