package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("QUEENSCANS", "QueenScans", "en")
internal class QueenScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.QUEENSCANS, pageSize = 30, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("queenscans.com")

	override val listUrl = "/comics"

}
