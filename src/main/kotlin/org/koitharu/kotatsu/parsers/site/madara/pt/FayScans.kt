package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FAYSCANS", "FayScans", "pt")
internal class FayScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.FAYSCANS, "fayscans.net") {
	override val datePattern: String = "dd/MM/yyyy"
}
