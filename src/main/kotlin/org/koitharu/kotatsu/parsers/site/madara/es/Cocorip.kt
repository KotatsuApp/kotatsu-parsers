package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("COCORIP", "Cocorip", "es")
internal class Cocorip(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.COCORIP, "cocorip.net", 16) {
	override val datePattern = "dd/MM/yyyy"
}
