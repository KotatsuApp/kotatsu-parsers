package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SEKTEDOUJIN", "Sektedoujin", "id", ContentType.HENTAI)
internal class Sektedoujin(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SEKTEDOUJIN, "sektedoujin.cc", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"

}
