package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MAGUSMANGA", "MagusManga", "en")
internal class MagusManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MAGUSMANGA, "vofeg.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
}
