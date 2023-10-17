package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAGALAXY", "MangaGalaxy", "en")
internal class MangaGalaxy(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAGALAXY, "mangagalaxy.me", 16) {

	override val datePattern = "MM/dd/yyyy"
}
