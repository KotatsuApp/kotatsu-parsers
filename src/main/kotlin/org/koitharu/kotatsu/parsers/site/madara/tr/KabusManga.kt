package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KABUSMANGA", "KabusManga", "tr")
internal class KabusManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.KABUSMANGA, "kabusmanga.com") {
	override val datePattern = "dd/MM/yyyy"
}
