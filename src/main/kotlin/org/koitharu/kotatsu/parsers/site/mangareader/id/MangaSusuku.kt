package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("MANGASUSUKU", "MangaSusuku", "id", ContentType.HENTAI)
internal class MangaSusuku(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANGASUSUKU, "mangasusuku.com", pageSize = 20, searchPageSize = 20) {
	override val listUrl = "/komik"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val isNetShieldProtected = true
}
