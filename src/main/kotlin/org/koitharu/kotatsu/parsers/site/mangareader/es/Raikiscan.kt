package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RAIKISCAN", "RaikiScan", "es")
internal class Raikiscan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.RAIKISCAN, "raikiscan.com", pageSize = 20, searchPageSize = 20) {
	override val datePattern = "MMM d, yyyy"
}
