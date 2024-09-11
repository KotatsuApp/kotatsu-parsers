package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ALTAYSCANS", "AltayScans", "en")
internal class AltayScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.ALTAYSCANS, "altayscans.com", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
