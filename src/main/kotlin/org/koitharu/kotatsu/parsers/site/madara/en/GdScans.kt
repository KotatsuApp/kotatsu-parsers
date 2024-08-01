package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GDSCANS", "GdScans", "en")
internal class GdScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.GDSCANS, "gdscans.com", 10) {
	override val tagPrefix = "webtoon-genre/"
}
