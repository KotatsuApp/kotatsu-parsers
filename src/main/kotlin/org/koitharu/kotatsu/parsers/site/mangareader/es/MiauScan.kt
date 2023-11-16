package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MIAUSCAN", "MiauScan", "es")
internal class MiauScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MIAUSCAN, "miaucomics.org", pageSize = 20, searchPageSize = 10)
