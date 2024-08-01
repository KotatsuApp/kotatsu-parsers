package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEVELING", "MangaLeveling", "en")
internal class MangaLeveling(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGALEVELING, "mangaleveling.com", 30) {
	override val postReq = true
	override val tagPrefix = "comics-genre/"
	override val listUrl = "comics/"
	override val datePattern = "MM/dd/yyyy"
}
