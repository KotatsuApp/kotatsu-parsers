package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SHOJOSCANS", "ShojoScans", "en")
internal class ShojoScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.SHOJOSCANS, "shojoscans.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/comics"
}
