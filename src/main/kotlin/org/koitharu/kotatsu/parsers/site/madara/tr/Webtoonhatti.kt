package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONHATTI", "WebtoonHatti", "tr")
internal class Webtoonhatti(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.WEBTOONHATTI, "webtoonhatti.net", 20) {
	override val listUrl = "webtoon/"
	override val tagPrefix = "webtoon-tur/"
	override val datePattern = "d MMMM"
}
