package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KOMIKMANHWA", "KomikManhwa", "id", ContentType.HENTAI)
internal class KomikManhwa(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKMANHWA, "komikmanhwa.me", pageSize = 20, searchPageSize = 20) {
	override val listUrl = "/series"
	override val datePattern = "MMM d, yyyy"
}
