package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("OZULSCANSEN", "OzulScans", "en")
internal class OzulScansEn(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.OZULSCANSEN, "ozulscansen.com", pageSize = 30, searchPageSize = 10) {
	override val listUrl = "/comics"
}
