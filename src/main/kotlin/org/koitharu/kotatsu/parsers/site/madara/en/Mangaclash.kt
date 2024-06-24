package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGACLASH", "MangaClash", "en")
internal class Mangaclash(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGACLASH, "mangaclash.com", pageSize = 18) {
	override val datePattern = "MM/dd/yyyy"
}
