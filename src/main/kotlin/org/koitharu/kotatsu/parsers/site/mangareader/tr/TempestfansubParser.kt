package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("TEMPESTFANSUB", "TempestFansub.Com", "tr")
internal class TempestfansubParser(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.TEMPESTFANSUB,
		"tempestscans.net",
		pageSize = 25,
		searchPageSize = 40,
	) {
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
