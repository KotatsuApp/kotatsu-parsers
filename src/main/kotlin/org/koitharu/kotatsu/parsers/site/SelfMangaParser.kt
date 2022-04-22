package org.koitharu.kotatsu.parsers.site

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("SELFMANGA", "SelfManga", "ru")
internal class SelfMangaParser(override val context: MangaLoaderContext) : GroupleParser(MangaSource.SELFMANGA) {

	override val configKeyDomain = ConfigKey.Domain("selfmanga.live", null)
}