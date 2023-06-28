package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat

@MangaSourceParser("BOOSEI", "Boosei", "id")
internal class Boosei(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.BOOSEI, pageSize = 30, searchPageSize = 30) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("boosei.net")

	override val listUrl: String
		get() = "/manga"
	override val tableMode: Boolean
		get() = false

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)
}
