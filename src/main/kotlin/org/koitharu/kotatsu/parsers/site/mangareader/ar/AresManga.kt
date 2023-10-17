package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ARESMANGA", "AresManga", "ar")
internal class AresManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ARESMANGA, "aresnov.org", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
}
