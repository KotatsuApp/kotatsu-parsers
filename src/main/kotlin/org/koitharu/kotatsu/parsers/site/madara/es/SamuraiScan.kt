package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SAMURAISCAN", "SamuraiScan", "es")
internal class SamuraiScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SAMURAISCAN, "latan.visorsmr.com", 10) {
	override val listUrl = "read/"
}
