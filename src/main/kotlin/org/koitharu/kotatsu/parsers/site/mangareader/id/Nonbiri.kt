package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("NONBIRI", "Nonbiri", "id")
internal class Nonbiri(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.NONBIRI, "nonbiri.space", pageSize = 15, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
}
