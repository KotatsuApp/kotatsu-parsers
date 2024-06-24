package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KNIGHTNOSCANLATION", "Knightno Scanlation", "es")
internal class KnightnoScanlation(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.KNIGHTNOSCANLATION, "knightnoscanlation.com") {
	override val listUrl = "sr/"
	override val tagPrefix = "generos/"
}
