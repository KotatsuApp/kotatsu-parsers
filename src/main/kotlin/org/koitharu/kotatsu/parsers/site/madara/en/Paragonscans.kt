package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PARAGONSCANS", "ParagonScans", "en")
internal class Paragonscans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.PARAGONSCANS, "paragonscans.com", pageSize = 50) {
	override val datePattern = "MM/dd/yyyy"
}
