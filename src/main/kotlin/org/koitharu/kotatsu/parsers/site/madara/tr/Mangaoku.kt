package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("MANGAOKU", "Mangaoku", "tr")
internal class Mangaoku(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAOKU, "mangaoku.info", 24) {
	override val datePattern = "dd MMMM yyyy"
	override val listUrl = "seri/"
	override val tagPrefix = "tur/"
}
