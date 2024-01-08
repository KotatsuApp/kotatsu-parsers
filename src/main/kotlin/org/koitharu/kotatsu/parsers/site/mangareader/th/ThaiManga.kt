package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("THAIMANGA", "ThaiManga", "th")
internal class ThaiManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.THAIMANGA, "www.thaimanga.net", pageSize = 40, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
