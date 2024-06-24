package org.koitharu.kotatsu.parsers.site.mangareader.cz

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("EVILMANGA", "EvilManga", "cs", ContentType.HENTAI)
internal class EvilManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.EVILMANGA, "evil-manga.eu", pageSize = 20, searchPageSize = 10) {
	override val datePattern = "d MMMM, yyyy"
}
