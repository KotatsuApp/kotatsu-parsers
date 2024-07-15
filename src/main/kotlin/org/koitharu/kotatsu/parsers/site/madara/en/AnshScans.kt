package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("ANSHSCANS", "AnshScans", "en")
internal class AnshScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ANSHSCANS, "anshscans.org", 10) {
	override val tagPrefix = "genre/"
	override val datePattern = "MMMM dd, yyyy"
}
