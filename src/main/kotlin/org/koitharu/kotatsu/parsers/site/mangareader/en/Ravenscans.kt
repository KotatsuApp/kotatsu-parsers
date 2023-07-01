package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RAVENSCANS", "Ravenscans", "en")
internal class Ravenscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.RAVENSCANS, pageSize = 10, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("ravenscans.com")

}
