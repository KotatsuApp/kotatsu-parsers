package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASIALOTUSS", "AsiaLotuss", "es")
internal class AsiaLotuss(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.ASIALOTUSS, "asialotuss.com", pageSize = 20, searchPageSize = 10)
