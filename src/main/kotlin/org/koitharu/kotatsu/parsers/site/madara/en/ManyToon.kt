package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANYTOON", "ManyToon", "en", ContentType.HENTAI)
internal class ManyToon(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANYTOON, "manytoon.com", 20) {
	override val listUrl = "comic/"
}
