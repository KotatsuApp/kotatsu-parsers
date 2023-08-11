package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MIAUSCAN", "Miau Scan", "es")
internal class MiauScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MIAUSCAN, "miauscans.com", pageSize = 20, searchPageSize = 20)
