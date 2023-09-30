package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWAX", "Manhwax", "en", ContentType.HENTAI)
internal class Manhwax(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWAX, "manhwax.org", pageSize = 20, searchPageSize = 20) {
	override val datePattern = "MMM d, yyyy"
}
