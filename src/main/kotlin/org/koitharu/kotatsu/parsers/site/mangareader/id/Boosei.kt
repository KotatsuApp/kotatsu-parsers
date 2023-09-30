package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("BOOSEI", "Boosei", "id")
internal class Boosei(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.BOOSEI, "boosei.net", pageSize = 30, searchPageSize = 30) {
	override val datePattern = "MMM d, yyyy"
}
