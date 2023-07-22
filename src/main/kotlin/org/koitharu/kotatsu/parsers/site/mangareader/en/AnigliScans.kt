package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ANIGLISCANS", "Anigli Scans", "en")
internal class AnigliScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ANIGLISCANS, "anigliscans.com", pageSize = 47, searchPageSize = 47) {

	override val listUrl = "/series"
	override val datePattern = "MMM d, yyyy"
}
