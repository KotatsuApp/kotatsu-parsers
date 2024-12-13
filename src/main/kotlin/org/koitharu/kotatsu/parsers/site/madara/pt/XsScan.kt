package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("XSSCAN", "XsScan", "pt")
internal class XsScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.XSSCAN, "xsscan.xyz") {
	override val datePattern: String = "dd/MM/yyyy"
}
