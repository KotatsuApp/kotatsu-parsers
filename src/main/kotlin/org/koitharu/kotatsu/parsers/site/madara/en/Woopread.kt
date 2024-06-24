package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WOOPREAD", "Woopread", "en")
internal class Woopread(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.WOOPREAD, "woopread.com", 10) {
	override val listUrl = "series/"
	override val tagPrefix = "series-genres/"
}
