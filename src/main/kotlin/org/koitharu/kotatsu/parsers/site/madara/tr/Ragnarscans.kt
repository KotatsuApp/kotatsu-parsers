package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RAGNARSCANS", "Ragnarscans", "tr")
internal class Ragnarscans(context: MangaLoaderContext) :
    MadaraParser(context, MangaParserSource.RAGNARSCANS, "ragnarscans.com", pageSize = 10) {
	  override val datePattern = "d MMMM yyyy"
}
