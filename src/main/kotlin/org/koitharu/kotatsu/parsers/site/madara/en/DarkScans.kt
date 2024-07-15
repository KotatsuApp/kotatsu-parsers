package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DARK_SCANS", "DarkScans", "en")
internal class DarkScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.DARK_SCANS, "darkscans.net", 18) {
	override val listUrl = "mangas/"
	override val tagPrefix = "mangas-genre/"
}
