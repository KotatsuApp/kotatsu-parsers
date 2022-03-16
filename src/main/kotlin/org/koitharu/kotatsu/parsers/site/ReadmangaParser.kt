package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class ReadmangaParser(override val context: MangaLoaderContext) : GroupleParser(MangaSource.READMANGA_RU) {

	override val configKeyDomain = ConfigKey.Domain("readmanga.io", null)
}