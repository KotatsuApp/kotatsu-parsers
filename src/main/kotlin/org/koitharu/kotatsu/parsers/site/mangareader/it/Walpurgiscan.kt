package org.koitharu.kotatsu.parsers.site.mangareader.it

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("WALPURGISCAN", "WalpurgiScan", "it")
internal class Walpurgiscan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.WALPURGISCAN, "www.walpurgiscan.it", pageSize = 20, searchPageSize = 20) {
	override val datePattern = "MMM d, yyyy"
}
