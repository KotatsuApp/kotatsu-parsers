package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAATREND", "MangaAtrend", "ar")
internal class MangaAtrend(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAATREND, "mangaatrend.net", pageSize = 30, searchPageSize = 10)
