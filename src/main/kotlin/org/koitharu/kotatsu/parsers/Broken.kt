package org.koitharu.kotatsu.parsers

/**
 * Annotate [MangaParser] implementation to mark this parser as broken instead of removing it
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
internal annotation class Broken(

	/**
	 * Reason why this parser is broken
	 */
	val message: String = "",
)
