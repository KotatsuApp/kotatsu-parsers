package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAFREAK", "MangaFreak", "en")
internal class MangaFreak(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAFREAK, "mangafreak.online") {
	override val postReq = true
	override val datePattern = "dd MMMM، yyyy"
}
