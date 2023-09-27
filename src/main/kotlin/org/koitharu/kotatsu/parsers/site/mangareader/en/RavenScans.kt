package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RAVENSCANS", "Ravenscans", "en")
internal class RavenScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.RAVENSCANS, "ravenscans.com", pageSize = 10, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
}
