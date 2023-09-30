package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ARVENSCANS", "Arven Scans", "en")
internal class ArvenScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ARVENSCANS, "arvenscans.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
}
