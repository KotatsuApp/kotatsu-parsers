package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("NIGHTSCANS", "Nightscans", "en")
internal class Nightscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.NIGHTSCANS, "nightscans.org", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"
}
