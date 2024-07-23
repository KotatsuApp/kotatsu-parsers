package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASTRASCANS", "AstraScans", "en")
internal class AstraScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.ASTRASCANS, "astrascans.org", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
	override val listUrl = "/series"
}
