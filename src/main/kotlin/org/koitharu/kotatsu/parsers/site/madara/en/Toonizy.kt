package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TOONIZY", "Toonizy", "en", ContentType.HENTAI)
internal class Toonizy(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TOONIZY, "toonizy.com", 24) {
	override val datePattern = "MMM d, yy"
	override val listUrl = "webtoon/"
}
