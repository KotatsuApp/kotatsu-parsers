package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.InternalParsersApi

@InternalParsersApi
class WordSet(private vararg val words: String) {

	fun anyWordIn(dateString: String): Boolean = words.any {
		dateString.contains(it, ignoreCase = true)
	}
}