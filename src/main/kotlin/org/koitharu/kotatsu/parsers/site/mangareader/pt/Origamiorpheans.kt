package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ORIGAMIORPHEANS", "Origami orpheans", "pt")
internal class Origamiorpheans(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaSource.ORIGAMIORPHEANS,
		"origami-orpheans.com.br",
		pageSize = 20,
		searchPageSize = 20,
	) {

	override val datePattern = "MMM d, yyyy"

}
