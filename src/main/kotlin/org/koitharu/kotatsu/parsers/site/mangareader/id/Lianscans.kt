package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LIANSCANS", "Lianscans", "id")
internal class Lianscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LIANSCANS, "www.lianscans.my.id", pageSize = 10, searchPageSize = 10) {

	override val isNsfwSource = true
	override val datePattern = "MMM d, yyyy"
}
