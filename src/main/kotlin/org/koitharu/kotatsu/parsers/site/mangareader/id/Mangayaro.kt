package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*


@MangaSourceParser("MANGAYARO", "Mangayaro", "id")
internal class Mangayaro(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAYARO, "mangayaro.net", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
