package org.koitharu.kotatsu.parsers

@Target(AnnotationTarget.CLASS)
annotation class MangaSourceParser(
	val name: String,
	val title: String,
	val locale: String = "",
)