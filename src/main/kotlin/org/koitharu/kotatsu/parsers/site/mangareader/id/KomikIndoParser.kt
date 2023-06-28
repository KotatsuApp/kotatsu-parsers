package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat

@MangaSourceParser("KOMIKINDO", "KomikIndo", "id")
internal class KomikIndoParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKINDO, pageSize = 20, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("komikindo.co")

	override val listUrl: String
		get() = "/project"
	override val tableMode: Boolean
		get() = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", idLocale)
}
