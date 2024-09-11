package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("JEAZTWOBLUESCANS", "Marcialhub", "es")
internal class JeazTwoBlueScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.JEAZTWOBLUESCANS, "marcialhub.xyz") {
	override val datePattern = "d MMMM, yyyy"
}
