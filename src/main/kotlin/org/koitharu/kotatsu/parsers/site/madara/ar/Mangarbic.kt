package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGARBIC", "MangaArabic", "ar")
internal class Mangarbic(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGARBIC, "mangarabic.com") {
	override val postReq = true
	override val datePattern = "yyyy/MM/dd"
}
