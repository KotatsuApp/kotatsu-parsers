package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YETISKINRUYAMANGA", "YaoiMangaOku", "tr")
internal class YetiskinRuyaManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.YETISKINRUYAMANGA, "www.yetiskinruyamanga.com", 16) {
	override val datePattern = "dD/MM/yyyy"
}
