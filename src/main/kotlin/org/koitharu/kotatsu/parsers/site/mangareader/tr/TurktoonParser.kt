package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.*

@MangaSourceParser("TURKTOON", "Turktoon", "tr")
internal class TurktoonParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TURKTOON, pageSize = 20, searchPageSize = 10) {

	override val configKeyDomain: ConfigKey.Domain = ConfigKey.Domain("turktoon.com")


	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale("tr", "TR"))

}
