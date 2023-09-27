package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KOMIKGO", "Komikgo", "id")
internal class KomikgoParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKGO, "komikgo.org", pageSize = 20, searchPageSize = 10) {
	override val datePattern = "MMM d, yyyy"
}
