package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@Broken
@MangaSourceParser("KOMIKLOKALCFD", "KomikLokal.mom", "id", ContentType.HENTAI)
internal class KomiklokalCfd(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KOMIKLOKALCFD, "komikmu.icu", pageSize = 30, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
}