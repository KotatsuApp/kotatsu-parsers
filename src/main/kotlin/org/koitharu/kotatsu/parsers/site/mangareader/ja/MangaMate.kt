package org.koitharu.kotatsu.parsers.site.mangareader.ja

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser
import java.text.SimpleDateFormat
import java.util.Locale

@MangaSourceParser("MANGAMATE", "Manga Mate", "ja")
internal class MangaMate(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAMATE, pageSize = 10, searchPageSize = 10) {
	override val configKeyDomain: ConfigKey.Domain
		get() = ConfigKey.Domain("manga-mate.org")


	override val chapterDateFormat: SimpleDateFormat = SimpleDateFormat("M月 d, yyyy", Locale.ENGLISH)
	override val encodedSrc = true

}
