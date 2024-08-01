package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TOONGOD", "ToonGod", "en", ContentType.HENTAI)
internal class ToonGod(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TOONGOD, "www.toongod.org", 18) {
	override val listUrl = "webtoon/"
	override val tagPrefix = "webtoon-genre/"
	override val datePattern = "d MMM yyyy"
	override val withoutAjax = true
}
