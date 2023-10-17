package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SKYMANGAS", "SkyMangas", "es")
internal class SkyMangas(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SKYMANGAS, "skymangas.com", pageSize = 20, searchPageSize = 10) {
	override val encodedSrc = true
}
