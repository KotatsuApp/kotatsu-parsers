package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("NYXMANGA", "NyxManga", "tr")
internal class NyxManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.NYXMANGA, "nyxmanga.com", pageSize = 14, searchPageSize = 10)

