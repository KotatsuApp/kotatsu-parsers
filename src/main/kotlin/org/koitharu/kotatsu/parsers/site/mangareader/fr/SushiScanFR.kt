package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SUSHISCANFR", "Sushi Scan FR", "fr")
internal class SushiScanFR(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SUSHISCANFR, "sushiscan.fr", pageSize = 36, searchPageSize = 10)
