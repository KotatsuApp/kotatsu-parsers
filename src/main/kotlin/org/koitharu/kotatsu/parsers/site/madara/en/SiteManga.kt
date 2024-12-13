package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SITEMANGA", "SiteManga", "en")
internal class SiteManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SITEMANGA, "sitemanga.com") {
	override val datePattern = "MM/dd/yyyy"
}
