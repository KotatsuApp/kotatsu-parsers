package org.koitharu.kotatsu.parsers.config

interface MangaSourceConfig {

	operator fun <T> get(key: ConfigKey<T>): T
}