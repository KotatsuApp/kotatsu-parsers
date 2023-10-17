package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ATLANTISSCAN", "AtlantisScan", "es")
internal class AtlantisScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ATLANTISSCAN, "atlantisscan.com", pageSize = 50) {
	override val datePattern = "dd/MM/yyyy"
}
