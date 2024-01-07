package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LIGHTSCANS", "LightScans", "en")
internal class LightScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LIGHTSCANS, "lightscans.fun", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
