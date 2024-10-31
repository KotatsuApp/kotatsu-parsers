package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("HUNTERSSCANEN", "EnHuntersScan", "en")
internal class HuntersScanEn(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HUNTERSSCANEN, "en.huntersscan.xyz") {
	override val datePattern = "MM/dd/yyyy"
	override val tagPrefix = "series-genre/"
	override val listUrl = "manga/"
}
