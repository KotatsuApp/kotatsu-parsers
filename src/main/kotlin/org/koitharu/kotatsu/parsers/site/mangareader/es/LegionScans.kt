package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("LEGIONSCANS", "Legion Scans", "es")
internal class LegionScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LEGIONSCANS, "legionscans.com", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH

}

