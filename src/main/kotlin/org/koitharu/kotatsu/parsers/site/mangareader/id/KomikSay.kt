package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("KOMIKSAY", "KomikSay", "id")
internal class KomikSay(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKSAY, "komiksay.info", pageSize = 30, searchPageSize = 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
}
