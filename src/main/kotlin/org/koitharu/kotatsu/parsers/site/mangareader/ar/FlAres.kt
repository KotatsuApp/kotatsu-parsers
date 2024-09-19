package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("FLARES", "Fl-Ares", "ar")
internal class FlAres(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.FLARES, "fl-ares.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
	override val encodedSrc = true
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
