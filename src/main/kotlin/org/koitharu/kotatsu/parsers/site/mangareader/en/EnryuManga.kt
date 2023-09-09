package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ENRYUMANGA", "Enryu Manga", "en")
internal class EnryuManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ENRYUMANGA, "enryumanga.com", pageSize = 30, searchPageSize = 10)
