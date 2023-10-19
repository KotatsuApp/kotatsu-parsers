package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KINGOFMANGA", "KingOfManga", "ar")
internal class KingOfManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KINGOFMANGA, "kingofmanga.com", pageSize = 30, searchPageSize = 10)
