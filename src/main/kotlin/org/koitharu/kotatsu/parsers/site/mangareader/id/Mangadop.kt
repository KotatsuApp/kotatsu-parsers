package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.Locale

@MangaSourceParser("MANGADOP", "Mangadop", "id", ContentType.HENTAI)
internal class Mangadop(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGADOP, "mangadop.net", pageSize = 20, searchPageSize = 20) {
	override val sourceLocale: Locale = Locale.ENGLISH
}
