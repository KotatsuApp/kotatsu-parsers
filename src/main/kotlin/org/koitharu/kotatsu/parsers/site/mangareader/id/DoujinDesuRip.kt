package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DOUJINDESURIP", "Doujin Desu Rip", "id")
internal class DoujinDesuRip(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DOUJINDESURIP, "doujindesu.rip", pageSize = 10, searchPageSize = 10) {

	override val isNsfwSource = true
	override val datePattern = "MMM d, yyyy"
}
