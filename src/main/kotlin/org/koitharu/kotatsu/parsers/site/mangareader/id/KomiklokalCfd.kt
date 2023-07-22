package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("KOMIKLOKALCFD", "Komiklokal Cfd", "id")
internal class KomiklokalCfd(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KOMIKLOKALCFD, pageSize = 30, searchPageSize = 10) {

	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("komiklokal.cfd")

	override val isNsfwSource = true
	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
}
