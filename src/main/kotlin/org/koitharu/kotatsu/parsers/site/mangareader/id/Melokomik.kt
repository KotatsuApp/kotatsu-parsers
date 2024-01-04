package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MELOKOMIK", "Wowomik", "id")
internal class Melokomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MELOKOMIK, "wowomik.com", pageSize = 20, searchPageSize = 10)
