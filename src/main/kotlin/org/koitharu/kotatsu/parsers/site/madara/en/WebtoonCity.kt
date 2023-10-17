package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONCITY", "WebtoonCity", "en")
internal class WebtoonCity(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONCITY, "webtooncity.com", 20) {
	override val listUrl = "webtoon/"
	override val tagPrefix = "webtoon-genre/"
}
