package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RAIJINSCANS", "RaijinScans", "fr")
internal class RaijinScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.RAIJINSCANS, "raijinscans.fr") {
	override val datePattern = "dd/MM/yyyy"
}
