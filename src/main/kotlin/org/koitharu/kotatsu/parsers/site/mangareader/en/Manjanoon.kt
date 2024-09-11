package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANJANOON_EN", "NoonScan.net", "en")
internal class Manjanoon(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANJANOON_EN, "noonscan.net", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
