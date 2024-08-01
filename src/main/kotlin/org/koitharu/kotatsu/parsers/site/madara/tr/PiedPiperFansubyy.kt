package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PIEDPIPERFANSUBYY", "PiedPiperFansubyy", "tr", ContentType.HENTAI)
internal class PiedPiperFansubyy(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.PIEDPIPERFANSUBYY, "piedpiperfansubyy.me", 18) {
	override val datePattern = "d MMMM yyyy"
}
