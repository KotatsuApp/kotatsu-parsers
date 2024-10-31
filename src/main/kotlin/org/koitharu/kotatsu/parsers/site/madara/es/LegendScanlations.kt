package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LEGENDSCANLATIONS", "LegendScanlations", "es")
internal class LegendScanlations(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.LEGENDSCANLATIONS, "legendscanlations.com", 10) {
	override val datePattern = "dd/MM/yyyy"
}
