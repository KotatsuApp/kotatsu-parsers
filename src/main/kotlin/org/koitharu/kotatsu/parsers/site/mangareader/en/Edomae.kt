package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("EDOMAE", "Edomae", "en")
internal class Edomae(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.EDOMAE, "edomae.co", pageSize = 20, searchPageSize = 10) {
	override val encodedSrc = true
	override val selectScript = "#content script"
}
