package org.koitharu.kotatsu.parsers.site.madara.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KINGS_MANGA", "KingsManga", "th")
internal class KingsManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.KINGS_MANGA, "www.kings-manga.co") {
	override val postReq = true
	override val datePattern = "d MMMM yyyy"
}
