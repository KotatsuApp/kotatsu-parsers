package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SURYASCANS", "SuryaScans", "en")
internal class SuryaScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.SURYASCANS, "suryacomics.com", pageSize = 5, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
