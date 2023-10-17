package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LYRASCANS", "LyraScans", "en")
internal class LyraScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LYRASCANS, "lyrascans.com", pageSize = 20, searchPageSize = 10)
