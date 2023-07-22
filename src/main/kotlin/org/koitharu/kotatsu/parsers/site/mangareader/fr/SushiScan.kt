package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SUSHISCAN", "SushiScan", "fr")
internal class SushiScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SUSHISCAN, "sushiscan.net", pageSize = 20, searchPageSize = 10) {

	override val listUrl = "/catalogue"
	override val datePattern = "MMM d, yyyy"
}
