package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("KOMIKLOKALCFD", "KomikLokal.sbs", "id", ContentType.HENTAI)
internal class KomiklokalCfd(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKLOKALCFD, "komiklokal.mom", pageSize = 30, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
}
