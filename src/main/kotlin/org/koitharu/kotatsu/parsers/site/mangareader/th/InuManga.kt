package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("INUMANGA", "Inu Manga", "th")
internal class InuManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.INUMANGA, "www.inu-manga.com", pageSize = 40, searchPageSize = 10)
