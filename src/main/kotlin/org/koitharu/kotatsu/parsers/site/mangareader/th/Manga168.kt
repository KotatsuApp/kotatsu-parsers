package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGA168", "Manga168", "th", ContentType.HENTAI)
internal class Manga168(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGA168, "manga168.com", pageSize = 40, searchPageSize = 30) {
	override val isTagsExclusionSupported = false
}
