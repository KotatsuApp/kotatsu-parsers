package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("POPSMANGA", "Pops Manga", "th")
internal class PopsManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.POPSMANGA, "popsmanga.com", pageSize = 20, searchPageSize = 14)
