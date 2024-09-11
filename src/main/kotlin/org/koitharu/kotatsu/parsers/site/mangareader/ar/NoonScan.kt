package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("NOONSCAN", "NoonScan.com", "ar")
internal class NoonScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.NOONSCAN, "noonscan.com", pageSize = 20, searchPageSize = 10)
