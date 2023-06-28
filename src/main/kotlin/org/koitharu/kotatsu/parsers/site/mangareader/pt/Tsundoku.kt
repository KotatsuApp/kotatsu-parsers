package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TSUNDOKU", "Tsundoku", "pt")
internal class Tsundoku(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TSUNDOKU, pageSize = 50, searchPageSize = 50) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("tsundoku.com.br")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

}
