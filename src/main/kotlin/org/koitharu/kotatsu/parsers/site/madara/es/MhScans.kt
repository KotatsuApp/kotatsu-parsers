package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MHSCANS", "MhScans", "es")
internal class MhScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MHSCANS, "twobluescans.com") {
	override val datePattern = "d 'de' MMMMM 'de' yyyy"
	override val listUrl = "series/"
}
