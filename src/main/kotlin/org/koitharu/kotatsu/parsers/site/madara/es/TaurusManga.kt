package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TAURUSMANGA", "TaurusManga", "es")
internal class TaurusManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TAURUSMANGA, "taurusmanga.com") {
	override val datePattern = "dd/MM/yyyy"
}
