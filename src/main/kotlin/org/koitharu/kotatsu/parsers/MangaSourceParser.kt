package org.koitharu.kotatsu.parsers

/**
 * Annotate each [MangaParser] implementation with this annotation, used by codegen
 */
@Target(AnnotationTarget.CLASS)
annotation class MangaSourceParser(
	/**
	 * Name of manga source. Used as an Enum value, must be UPPER_CASE and unique.
	 */
	val name: String,
	/**
	 * User-friendly title of manga source. In most case equals the website name.
	 */
	val title: String,
	/**
	 * Language code (for example "en" or "ru") or blank if parser provide manga on different languages.
	 */
	val locale: String = "",
)