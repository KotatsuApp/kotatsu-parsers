package org.koitharu.kotatsu.parsers.site.zeistmanga.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("MANGAATREND", "MangaATrend", "ar")
internal class Mangaatrend(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaSource.MANGAATREND, "www.mangaatrend.net") {
	override val selectPage = "#seoneurons-target img"
}
