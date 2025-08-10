package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAEFENDISI", "MangaEfendisi", "tr")
internal class MangaGezgini(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAEFENDISI, "mangaefendisi.net", pageSize = 20) {
	override val datePattern = "dd/MM/yyyy"
}
