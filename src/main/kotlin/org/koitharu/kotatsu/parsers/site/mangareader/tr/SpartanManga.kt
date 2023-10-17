package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SPARTANMANGA", "SpartanManga", "tr")
internal class SpartanManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SPARTANMANGA, "spartanmanga.com.tr", pageSize = 40, searchPageSize = 20)

