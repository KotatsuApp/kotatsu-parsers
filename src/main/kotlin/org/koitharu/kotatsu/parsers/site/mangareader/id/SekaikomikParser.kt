package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SEKAIKOMIK", "Sekai Komik", "id")
internal class SekaikomikParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SEKAIKOMIK, "sekaikomik.pro", pageSize = 20, searchPageSize = 100)
