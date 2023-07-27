package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("KOMIKDEWASA", "KomikDewasa", "id", ContentType.HENTAI)
internal class KomikDewasaParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKDEWASA, "komikdewasa.cfd", pageSize = 20, searchPageSize = 20) {

	override val listUrl: String = "/komik"
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
