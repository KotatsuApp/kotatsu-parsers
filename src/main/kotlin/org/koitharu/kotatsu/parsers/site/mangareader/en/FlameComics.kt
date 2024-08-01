package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("FLAMECOMICS", "FlameComics", "en")
internal class FlameComics(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.FLAMECOMICS, "flamecomics.me", pageSize = 24, searchPageSize = 10) {
	override val listUrl = "/series"
}
