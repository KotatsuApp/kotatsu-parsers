package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("PATIMANGA", "PatiManga", "tr")
internal class PatiManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.PATIMANGA, "www.patimanga.com", pageSize = 20, searchPageSize = 10)

