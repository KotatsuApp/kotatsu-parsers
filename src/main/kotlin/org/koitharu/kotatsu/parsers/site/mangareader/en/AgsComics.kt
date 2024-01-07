package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("AGSCOMICS", "AgsComics", "en")
internal class AgsComics(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.AGSCOMICS, "agscomics.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
	override val isTagsExclusionSupported = false
}
