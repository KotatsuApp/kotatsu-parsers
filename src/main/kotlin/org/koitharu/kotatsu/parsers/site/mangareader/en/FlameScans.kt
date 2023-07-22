package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("FLAMESCANS", "Flame Scans", "en")
internal class FlameScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.FLAMESCANS, "flamescans.org", pageSize = 20, searchPageSize = 20) {

	override val listUrl = "/series"
	override val datePattern = "MMM d, yyyy"
}
