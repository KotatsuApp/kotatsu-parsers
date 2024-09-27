package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@Broken
@MangaSourceParser("YDCOMICS", "YdComics", "en")
internal class YdComics(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.YDCOMICS, "yd-comics.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/index.php/series"
}
