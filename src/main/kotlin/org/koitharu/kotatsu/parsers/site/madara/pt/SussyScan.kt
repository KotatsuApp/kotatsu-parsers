package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SUSSYSCAN", "SussyScan", "pt")
internal class SussyScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SUSSYSCAN, "sussyscan.com") {
	override val datePattern: String = "dd/MM/yyyy"
}
