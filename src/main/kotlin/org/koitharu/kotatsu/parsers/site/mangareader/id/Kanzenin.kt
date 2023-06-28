package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("KANZENIN", "Kanzenin", "id")
internal class Kanzenin(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KANZENIN, pageSize = 25, searchPageSize = 25) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("kanzenin.xyz")

	override val listUrl: String
		get() = "/manga"
	override val tableMode: Boolean
		get() = false

	override val isNsfwSource = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
}
