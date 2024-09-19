package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RAINDROPTEAMFAN", "Raindrop Fansub", "tr")
internal class Raindropteamfan(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.RAINDROPTEAMFAN,
		"www.raindropteamfan.com",
		pageSize = 25,
		searchPageSize = 10,
	) {
	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}

