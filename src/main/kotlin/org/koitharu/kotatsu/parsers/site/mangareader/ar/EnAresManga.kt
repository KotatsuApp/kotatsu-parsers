package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ENARESMANGA", "EnAresManga", "ar")
internal class EnAresManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ENARESMANGA, "en-aresmanga.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
}
