package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LUNARSCANS", "LunarScans", "fr")
internal class LunarScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LUNARSCANS, "lunarscans.fr", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
