package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DUNIAKOMIK", "Duniakomik", "id", ContentType.HENTAI)
internal class Duniakomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DUNIAKOMIK, "duniakomik.id", pageSize = 12, searchPageSize = 12) {

	override val datePattern = "MMM d, yyyy"
}
