package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BOKUGEN", "Bokugen", "es")
internal class Bokugen(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.BOKUGEN, "bokugents.com") {
	override val datePattern = "dd/MM/yyyy"
}
