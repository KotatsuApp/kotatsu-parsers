package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DOJING", "Dojing", "id")
internal class Dojing(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DOJING, "dojing.net", pageSize = 12, searchPageSize = 12) {

	override val isNsfwSource = true
	override val datePattern = "MMM d, yyyy"
}
