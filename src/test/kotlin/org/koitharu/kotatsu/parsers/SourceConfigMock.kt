package org.koitharu.kotatsu.parsers

import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.config.MangaSourceConfig

internal class SourceConfigMock : MangaSourceConfig {

	override fun <T> get(key: ConfigKey<T>): T = key.defaultValue
}