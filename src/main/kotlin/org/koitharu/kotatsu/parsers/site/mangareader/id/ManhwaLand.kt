package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWALAND", "ManhwaLand", "id", ContentType.HENTAI)
internal class ManhwaLand(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWALAND, "62.182.80.253", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
