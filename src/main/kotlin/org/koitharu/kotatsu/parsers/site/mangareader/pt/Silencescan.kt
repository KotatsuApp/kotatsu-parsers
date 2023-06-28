package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("SILENCESCAN", "Silencescan", "pt")
internal class Silencescan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SILENCESCAN, pageSize = 35, searchPageSize = 35) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("silencescan.com.br")

	override val isNsfwSource = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale("pt", "PT"))

}
