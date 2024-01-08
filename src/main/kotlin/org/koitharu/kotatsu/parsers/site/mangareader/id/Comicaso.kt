package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("COMICASO", "Comicaso", "id")
internal class Comicaso(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.COMICASO, "comicaso.com", pageSize = 20, searchPageSize = 10) {
	override val encodedSrc = true
	override val isTagsExclusionSupported = false
}
