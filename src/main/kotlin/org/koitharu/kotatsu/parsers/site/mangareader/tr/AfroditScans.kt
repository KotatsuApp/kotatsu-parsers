package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("AFRODITSCANS", "Afrodit Scans", "tr")
internal class AfroditScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.AFRODITSCANS, "afroditscans.com", pageSize = 24, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
}
