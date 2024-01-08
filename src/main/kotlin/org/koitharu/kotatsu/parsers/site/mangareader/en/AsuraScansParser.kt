package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASURASCANS", "AsuraScans", "en")
internal class AsuraScansParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ASURASCANS, "asuratoon.com", pageSize = 20, searchPageSize = 10) {
	override val selectPage = "#readerarea img:not(.asurascans)"
	override val selectTestScript = "force html"
	override val isTagsExclusionSupported = false
}
