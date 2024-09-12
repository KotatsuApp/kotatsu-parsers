package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HUNTERSSCAN", "HuntersScan", "pt")
internal class HuntersScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HUNTERSSCAN, "hunterscomics.com", pageSize = 50) {
	override val datePattern = "MM/dd/yyyy"
	override val tagPrefix = "series-genre/"
	override val listUrl = "series/"
}
