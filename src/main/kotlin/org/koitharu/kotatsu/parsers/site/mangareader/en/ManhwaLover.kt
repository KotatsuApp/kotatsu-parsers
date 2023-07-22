package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWALOVER", "ManhwaLover", "en")
internal class ManhwaLover(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWALOVER, "manhwalover.com", pageSize = 20, searchPageSize = 20) {

	override val isNsfwSource: Boolean = true
	override val datePattern = "MMM d, yyyy"
}
