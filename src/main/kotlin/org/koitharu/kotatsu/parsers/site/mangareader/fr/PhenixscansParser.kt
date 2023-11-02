package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("PHENIXSCANS", "PhenixScans", "fr")
internal class PhenixscansParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.PHENIXSCANS, "phenixscans.fr", pageSize = 20, searchPageSize = 10) {
	override val datePattern = "MMM d, yyyy"
}
