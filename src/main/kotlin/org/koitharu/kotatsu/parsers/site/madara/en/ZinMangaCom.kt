package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZIN_MANGA_COM", "Zin-Manga.com", "en")
internal class ZinMangaCom(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ZIN_MANGA_COM, "zin-manga.com") {
	override val selectPage = "img"
}
