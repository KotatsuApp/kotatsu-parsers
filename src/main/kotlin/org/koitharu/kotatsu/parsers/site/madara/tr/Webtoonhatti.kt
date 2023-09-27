package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONHATTI", "Webtoon Hatti", "tr")
internal class Webtoonhatti(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONHATTI, "webtoonhatti.net", 20) {
	override val listUrl = "webtoon/"
	override val tagPrefix = "webtoon-tur/"
	override val datePattern = "d MMMM"
}
