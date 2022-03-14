package org.koitharu.kotatsu.parsers

interface MangaSourceConfig {
	fun getDomain(defaultValue: String): String
	fun isSslEnabled(defaultValue: Boolean): Boolean
}