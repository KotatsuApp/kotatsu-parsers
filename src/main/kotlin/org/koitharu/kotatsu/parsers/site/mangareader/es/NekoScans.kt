package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("NEKOSCANS", "NekoScans", "es")
internal class NekoScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.NEKOSCANS, "nekoscans.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/proyecto"
	override val encodedSrc = true
	override val isTagsExclusionSupported = false
}
