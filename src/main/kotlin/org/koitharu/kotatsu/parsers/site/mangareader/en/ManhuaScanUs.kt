package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("MANHUASCANUS", "Manhua Scan Us", "en", ContentType.HENTAI)
internal class ManhuaScanUs(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHUASCANUS, "manhuascan.us", pageSize = 30, searchPageSize = 30) {

	override val datePattern = "dd-MM-yyyy"
	override val listUrl = "/manga-list"

}
