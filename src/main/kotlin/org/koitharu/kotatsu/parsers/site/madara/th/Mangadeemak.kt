package org.koitharu.kotatsu.parsers.site.madara.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGADEEMAK", "MangaDeemak", "th")
internal class Mangadeemak(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGADEEMAK, "mangadeemak.com", 12) {
	override val datePattern: String = "d MMMM yyyy"
}
