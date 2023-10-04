package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("WARUNGKOMIK", "Warung Komik", "id")
internal class WarungKomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.WARUNGKOMIK, "warungkomik.com", pageSize = 20, searchPageSize = 10)
