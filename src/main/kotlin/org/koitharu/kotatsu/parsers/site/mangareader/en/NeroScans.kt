package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("NEROSCANS", "NeroScans", "en")
internal class NeroScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.NEROSCANS, "neroscans.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
	override val isTagsExclusionSupported = false
}
