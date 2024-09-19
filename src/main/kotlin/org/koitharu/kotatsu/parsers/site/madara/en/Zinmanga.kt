package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZINMANGA", "ZinManga.net", "en")
internal class Zinmanga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ZINMANGA, "zinmanga.net") {
	override val datePattern = "MM/dd/yyyy"
	override val withoutAjax = true
}
