package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASURASCANS", "Asura Scans", "en")
internal class AsuraScansParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ASURASCANS, "asura.nacm.xyz", pageSize = 20, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
	override val selectPage = "div#readerarea p img"

	// A little dummy text to avoid importing the whole getpage part
	override val selectTestScript = "Force to parse html"
}
