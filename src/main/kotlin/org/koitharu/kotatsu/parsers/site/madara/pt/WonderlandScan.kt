package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WONDERLANDSCAN", "WonderlandScan", "pt")
internal class WonderlandScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.WONDERLANDSCAN, "wonderlandscan.com") {
	override val datePattern: String = "dd/MM/yyyy"
}
