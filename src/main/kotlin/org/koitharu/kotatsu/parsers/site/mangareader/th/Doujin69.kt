package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DOUJIN69", "Doujin69", "th")
internal class Doujin69(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DOUJIN69, "doujin69.com", pageSize = 40, searchPageSize = 21) {

	override val isNsfwSource = true
	override val listUrl = "/doujin"
}
