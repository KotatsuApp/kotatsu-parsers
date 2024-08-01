package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("CABAREDOWATAME", "DessertScan", "pt")
internal class Cabaredowatame(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.CABAREDOWATAME, "cabaredowatame.site", 10) {
	override val datePattern = "dd/MM/yyyy"
}
