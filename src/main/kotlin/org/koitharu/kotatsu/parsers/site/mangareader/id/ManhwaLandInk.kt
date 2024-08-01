package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWALAND_INK", "ManhwaLand.ink", "id", ContentType.HENTAI)
internal class ManhwaLandInk(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.MANHWALAND_INK,
		"www.manhwaland.ink",
		pageSize = 20,
		searchPageSize = 10,
	) {
	override val isTagsExclusionSupported = false
	override val datePattern = "MMM d, yyyy"
}
