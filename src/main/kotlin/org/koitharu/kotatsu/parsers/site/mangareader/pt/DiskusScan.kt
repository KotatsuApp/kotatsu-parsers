package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DISKUSSCAN", "DiskusScan", "pt")
internal class DiskusScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.DISKUSSCAN, "diskusscan.online", pageSize = 20, searchPageSize = 10)
