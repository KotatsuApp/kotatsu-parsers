package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("WEBTOONHATTI", "Webtoonhatti", "tr")
internal class Webtoonhatti(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONHATTI, "webtoonhatti.com", 20) {
	override val tagPrefix = "webtoon-tur/"

	override val datePattern = "d MMMM"
	override val sourceLocale: Locale = Locale("tr")

}
