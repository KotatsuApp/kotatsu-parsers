package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("READKOMIK", "ReadKomik", "en")
internal class Readkomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.READKOMIK, "readkomik.com", pageSize = 20, searchPageSize = 20) {
	override val datePattern = "MMM d, yyyy"
}

