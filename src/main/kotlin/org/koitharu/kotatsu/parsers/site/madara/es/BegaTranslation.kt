package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BEGATRANSLATION", "BegaTranslation", "es")
internal class BegaTranslation(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.BEGATRANSLATION, "begatranslation.com") {
	override val datePattern = "dd/MM/yyyy"
	override val listUrl = "series/"
}
