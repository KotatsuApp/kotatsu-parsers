package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAPROTM", "MangaProtm", "ar")
internal class MangaProtm(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAPROTM, "mangaprotm.com", pageSize = 20, searchPageSize = 20) {

	override val listUrl = "/series"
}
