package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("VOIDSCANS", "VoidScans", "en")
internal class VoidScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.VOIDSCANS, "void-scans.com", pageSize = 150, searchPageSize = 150) {
	override val datePattern = "MMM d, yyyy"
	override val isTagsExclusionSupported = false
}
