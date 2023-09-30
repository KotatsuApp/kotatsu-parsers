package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("MANGAINDO", "Manga Indo", "id")
internal class Mangaindo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAINDO, "mangaindo.me", pageSize = 26, searchPageSize = 26) {
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
