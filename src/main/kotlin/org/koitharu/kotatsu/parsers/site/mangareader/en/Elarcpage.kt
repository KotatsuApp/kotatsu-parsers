package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ELARCPAGE", "Elarcpage", "en")
internal class Elarcpage(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ELARCPAGE, "elarcpage.com", pageSize = 20, searchPageSize = 10) {

	override val listUrl = "/series"
	override val datePattern = "MMM d, yyyy"
}
