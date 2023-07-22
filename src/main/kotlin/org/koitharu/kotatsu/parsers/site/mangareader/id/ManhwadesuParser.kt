package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWADESU", "ManhwaDesu", "id")
internal class ManhwadesuParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWADESU, "manhwadesu.top", pageSize = 20, searchPageSize = 10) {

	override val listUrl = "/komik"
	override val datePattern = "MMM d, yyyy"
}
