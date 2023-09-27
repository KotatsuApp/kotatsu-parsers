package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("KOMIKMANGA", "Komik Manga", "id")
internal class KomikMangaParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKMANGA, "komikhentai.co", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/project"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH

}
