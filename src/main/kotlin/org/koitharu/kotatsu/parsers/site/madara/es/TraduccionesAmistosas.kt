package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TRADUCCIONESAMISTOSAS", "TraduccionesAmistosas", "es")
internal class TraduccionesAmistosas(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TRADUCCIONESAMISTOSAS, "traduccionesamistosas.eyudud.net", 10) {
	override val datePattern = "d 'de' MMMMM 'de' yyyy"
}
