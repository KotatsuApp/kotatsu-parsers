package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("FURYMANGA", "KingOfScans", "en")
internal class FuryManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.FURYMANGA, "myshojo.com", pageSize = 30, searchPageSize = 10) {
	override val listUrl = "/comics"
}
