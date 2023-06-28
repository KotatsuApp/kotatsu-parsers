package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("EPSILONSCAN", "Epsilonscan", "fr")
internal class EpsilonscanParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.EPSILONSCAN, pageSize = 20, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("epsilonscan.fr")

	override val listUrl: String
		get() = "/manga"
	override val tableMode: Boolean
		get() = false
	override val isNsfwSource: Boolean = true

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)
}
