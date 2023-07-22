package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DUNIAKOMIK", "Duniakomik", "id")
internal class Duniakomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DUNIAKOMIK, "duniakomik.id", pageSize = 12, searchPageSize = 12) {

	override val isNsfwSource = true
	override val datePattern = "MMM d, yyyy"
}
