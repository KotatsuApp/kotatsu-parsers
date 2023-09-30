package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KAISCANS", "Kai Scans", "en")
internal class KaiScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KAISCANS, "kaiscans.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
}
