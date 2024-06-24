package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("CROWSCANS", "CrowScans", "ar")
internal class CrowScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.CROWSCANS, "crowscans.com", pageSize = 30, searchPageSize = 10)
