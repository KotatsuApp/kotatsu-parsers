package org.koitharu.kotatsu.parsers

internal class SourceConfigMock : MangaSourceConfig {

	override fun getDomain(defaultValue: String): String = defaultValue

	override fun isSslEnabled(defaultValue: Boolean): Boolean = defaultValue
}