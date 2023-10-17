package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SHADOWMANGAS", "ShadowMangas", "es")
internal class Shadowmangas(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SHADOWMANGAS, "shadowmangas.com", pageSize = 10, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
}
