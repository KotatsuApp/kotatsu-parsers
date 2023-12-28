package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANHWADESU", "ManhwaDesu", "id", ContentType.HENTAI)
internal class ManhwadesuParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWADESU, "manhwadesu.bio", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/komik"
}
