package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LUNARHENTAI", "LunarHentai", "fr", ContentType.HENTAI)
internal class LunarHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.LUNARHENTAI, "hentai.lunarscans.fr") {
	override val datePattern = "dd MMMM yyyy"
}
