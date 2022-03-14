package org.koitharu.kotatsu.parsers.model

data class MangaPage(
	val id: Long,
	val url: String,
	val referer: String,
	val preview: String?,
	val source: MangaSource,
)