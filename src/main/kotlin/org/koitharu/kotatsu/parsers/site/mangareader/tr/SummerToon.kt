package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SUMMERTOON", "SummerToon", "tr")
internal class SummerToon(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SUMMERTOON, "summertoon.com", pageSize = 10, searchPageSize = 10)

