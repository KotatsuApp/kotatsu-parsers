package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("WEBTOONEMPIRE", "WebtoonEmpire", "ar")
internal class WebtoonEmpire(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.WEBTOONEMPIRE, "webtoonsempireron.com", pageSize = 10) {
	override val listUrl = "webtoon/"
	override val datePattern = "d MMMM، yyyy"
}
