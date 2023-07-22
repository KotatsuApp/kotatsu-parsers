package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("QUEENSCANS", "QueenScans", "en")
internal class QueenScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.QUEENSCANS, "queenscans.com", pageSize = 30, searchPageSize = 10) {

	override val listUrl = "/comics"
	override val datePattern = "MMM d, yyyy"
}
