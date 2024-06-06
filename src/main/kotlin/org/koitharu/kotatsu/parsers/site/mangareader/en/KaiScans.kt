package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KAISCANS", "KaiScans", "en")
internal class KaiScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KAISCANS, "kaiscans.org", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
	override val isTagsExclusionSupported = false
}
