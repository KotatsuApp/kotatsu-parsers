package org.koitharu.kotatsu.parsers.model

import org.koitharu.kotatsu.parsers.InternalParsersApi

@InternalParsersApi
public class WordSet(private vararg val words: String) {

	public fun anyWordIn(dateString: String): Boolean = words.any {
		dateString.contains(it, ignoreCase = true)
	}
}
