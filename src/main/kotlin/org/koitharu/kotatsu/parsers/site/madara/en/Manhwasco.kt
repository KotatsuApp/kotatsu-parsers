package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWASCO", "ManhwaSco", "en")
internal class Manhwasco(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHWASCO, "manhwasco.net") {
	override val selectGenre = "div.mg_genres a"
}
