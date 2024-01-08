package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.util.*

@MangaSourceParser("TUKANGKOMIK", "Tukang Komik", "id")
internal class Tukangkomik(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TUKANGKOMIK, "tukangkomik.id", pageSize = 20, searchPageSize = 20) {
	override val datePattern = "MMM d, yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
	override val isTagsExclusionSupported = false
}
