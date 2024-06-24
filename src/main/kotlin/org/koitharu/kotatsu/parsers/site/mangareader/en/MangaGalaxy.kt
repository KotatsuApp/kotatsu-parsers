package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAGALAXY", "MangaGalaxy", "en")
internal class MangaGalaxy(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANGAGALAXY, "mangagalaxy.me", 20, 16) {
	override val listUrl = "/series"
}
