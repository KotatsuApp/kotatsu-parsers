package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ANISASCANS", "AnisaScans", "en")
internal class AnisaScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ANISASCANS, "anisascans.in", 36) {
	override val datePattern = "dd MMM, yyyy"
}
