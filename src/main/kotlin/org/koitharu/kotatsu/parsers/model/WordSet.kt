package org.koitharu.kotatsu.parsers.model

internal class WordSet(private vararg val words: String) {

	fun anyWordIn(dateString: String): Boolean = words.any {
		dateString.contains(it, ignoreCase = true)
	}
}