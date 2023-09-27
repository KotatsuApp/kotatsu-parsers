package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("KATAKOMIK", "Katakomik", "id")
internal class KataKomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KATAKOMIK, "katakomik.online", pageSize = 20, searchPageSize = 20) {

	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH

}
