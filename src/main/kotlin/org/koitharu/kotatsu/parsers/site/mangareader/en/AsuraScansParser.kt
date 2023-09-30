package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASURASCANS", "Asura Scans", "en")
internal class AsuraScansParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ASURASCANS, "asuratoon.com", pageSize = 20, searchPageSize = 10) {
	override val selectPage = "div#readerarea p img"
	override val selectTestScript = "force html"
}
