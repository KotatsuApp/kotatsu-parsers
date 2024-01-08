package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("FREAKSCANS", "FreakScans", "en")
internal class FreakScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.FREAKSCANS, "freakscans.com", pageSize = 20, searchPageSize = 20) {
	override val datePattern = "MMM d, yyyy"
	override val isTagsExclusionSupported = false
}
