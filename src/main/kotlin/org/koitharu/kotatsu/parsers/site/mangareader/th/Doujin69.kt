package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DOUJIN69", "Doujin69", "th", type = ContentType.HENTAI)
internal class Doujin69(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.DOUJIN69, "doujin69.com", pageSize = 40, searchPageSize = 21) {
	override val listUrl = "/doujin"

	override val filterCapabilities: MangaListFilterCapabilities
		get() = super.filterCapabilities.copy(
			isTagsExclusionSupported = false,
		)
}
