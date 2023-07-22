package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGA689", "Manga689", "th")
internal class Manga689(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGA689, "manga689.com", pageSize = 45, searchPageSize = 21)
