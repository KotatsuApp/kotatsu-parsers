package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SPIDERSCANS", "SpiderScans", "ar")
internal class SpiderScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SPIDERSCANS, "spiderscans.com", pageSize = 20, searchPageSize = 10)
