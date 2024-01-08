package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DOUJINDESURIP", "DoujinDesu.cfd", "id", ContentType.HENTAI)
internal class DoujinDesuRip(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.DOUJINDESURIP, "doujindesu.cfd", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
