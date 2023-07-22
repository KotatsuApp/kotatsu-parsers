package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale


@MangaSourceParser("PISCANS", "Piscans", "id")
internal class Piscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.PISCANS, "piscans.in", pageSize = 24, searchPageSize = 24) {

	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
