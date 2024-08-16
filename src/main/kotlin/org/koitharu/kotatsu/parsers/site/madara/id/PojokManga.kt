package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("POJOKMANGA", "PojokManga", "id")
internal class PojokManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.POJOKMANGA, "pojokmanga.info") {
	override val tagPrefix = "komik-genre/"
	override val listUrl = "komik/"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
