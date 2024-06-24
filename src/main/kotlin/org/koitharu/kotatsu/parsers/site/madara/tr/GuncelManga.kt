package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GUNCEL_MANGA", "GuncelManga", "tr")
internal class GuncelManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.GUNCEL_MANGA, "guncelmanga.net") {
	override val datePattern = "d MMMM yyyy"
}
