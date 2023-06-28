package org.koitharu.kotatsu.parsers.site.mangareader.it

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("WALPURGISCAN", "Walpurgiscan", "it")
internal class Walpurgiscan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.WALPURGISCAN, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("walpurgiscan.it")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("it", "IT"))

}
