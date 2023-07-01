package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("MANHWAINDO", "Manhwaindo", "id")
internal class ManhwaIndoParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANHWAINDO, pageSize = 30, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("manhwaindo.id")

	override val chapterDateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale.ENGLISH)
	override val listUrl: String get() = "/series"
}
