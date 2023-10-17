package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LIANSCANS", "LianScans", "id", ContentType.HENTAI)
internal class Lianscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LIANSCANS, "www.lianscans.my.id", pageSize = 10, searchPageSize = 10) {

	override val datePattern = "MMM d, yyyy"
}
