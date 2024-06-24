package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONTR", "WebtoonTr", "tr")
internal class Webtoontr(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.WEBTOONTR, "webtoontr.net", 16) {
	override val tagPrefix = "webtoon-kategori/"
	override val listUrl = "webtoon/"
	override val datePattern = "dd/MM/yyyy"
}
