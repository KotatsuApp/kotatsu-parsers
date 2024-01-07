package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("MANHWAINDO", "ManhwaIndo.id", "id")
internal class ManhwaIndoParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWAINDO, "manhwaindo.id", pageSize = 30, searchPageSize = 10) {
	override val datePattern = "MMMM dd, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "/series"
}
