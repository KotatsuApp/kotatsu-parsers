package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("WEBTOON", "Webtoon.uk", "en")
internal class Webtoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.WEBTOON, "webtoon.uk", 20) {
	override val tagPrefix = "manhwa-genre/"
	override val postReq = true
}
