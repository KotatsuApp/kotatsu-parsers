package org.koitharu.kotatsu.parsers.model

data class MangaChapter(
	val id: Long,
	val name: String,
	val number: Int,
	val url: String,
	val scanlator: String?,
	val uploadDate: Long,
	val branch: String?,
	val source: MangaSource,
) : Comparable<MangaChapter> {

	override fun compareTo(other: MangaChapter): Int {
		return number.compareTo(other.number)
	}
}