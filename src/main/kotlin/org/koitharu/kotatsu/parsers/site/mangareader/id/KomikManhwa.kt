package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat

@MangaSourceParser("KOMIKMANHWA", "Komik Manhwa", "id")
internal class KomikManhwa(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKMANHWA, pageSize = 20, searchPageSize = 20) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("komikmanhwa.me")

	override val listUrl: String
		get() = "/series"
	override val tableMode: Boolean
		get() = true

	override val isNsfwSource = true


	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)

}
