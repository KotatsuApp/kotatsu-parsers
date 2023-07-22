package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SILENCESCAN", "Silencescan", "pt")
internal class Silencescan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SILENCESCAN, "silencescan.com.br", pageSize = 35, searchPageSize = 35) {

	override val isNsfwSource = true
	override val datePattern = "MMM d, yyyy"
}
