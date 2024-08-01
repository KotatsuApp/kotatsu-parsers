package org.koitharu.kotatsu.parsers.site.madara.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALC", "MangaLc", "th")
internal class MangaLc(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGALC, "manga-lc.net", 24) {
	override val datePattern: String = "d MMMM yyyy"
	override val selectPage = "img"
}
