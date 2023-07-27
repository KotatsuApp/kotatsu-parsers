package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("MANGAKITA", "MangaKita", "id")
internal class MangakKita(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAKITA, "mangakita.net", pageSize = 20, searchPageSize = 20) {


	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
