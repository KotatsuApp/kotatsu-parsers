package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LAMIMANGA", "LamiManga", "th")
internal class LamiManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LAMIMANGA, "www.lami-manga.com", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
