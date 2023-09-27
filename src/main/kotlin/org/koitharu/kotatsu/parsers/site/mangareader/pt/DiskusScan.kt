package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DISKUSSCAN", "Diskus Scan", "pt")
internal class DiskusScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DISKUSSCAN, "diskusscan.com", pageSize = 20, searchPageSize = 10)
