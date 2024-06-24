package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASURASCANS", "AsuraScans", "en")
internal class AsuraScansParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.ASURASCANS, "asuratoon.com", pageSize = 20, searchPageSize = 10) {
	override val selectPage = "#readerarea img:not(.asurascans)"
	override val selectTestScript = "force html"
	override val isTagsExclusionSupported = false
}
