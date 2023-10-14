package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAATREND", "Manga A Trend", "ar")
internal class Mangaatrend(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAATREND, "mangaatrend.net", pageSize = 40, searchPageSize = 20)
