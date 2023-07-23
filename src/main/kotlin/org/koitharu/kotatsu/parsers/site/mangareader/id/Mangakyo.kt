package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAKYO", "Mangakyo", "id")
internal class Mangakyo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAKYO, "mangakyo.org", pageSize = 40, searchPageSize = 20) {

	override val listUrl = "/komik"
	override val datePattern = "MMM d, yyyy"
}
