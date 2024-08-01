package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("STONESCAPE", "StoneScape", "en")
internal class StoneScape(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.STONESCAPE, "stonescape.xyz", 10) {
	override val listUrl = "series/"
	override val tagPrefix = "series-genre/"
}
