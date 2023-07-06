package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("TEMPESTFANSUB", "Tempestfansub", "tr")
internal class TempestfansubParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TEMPESTFANSUB, pageSize = 25, searchPageSize = 40) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("manga.tempestfansub.com")

	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

}
