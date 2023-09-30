package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGACLASH", "Manga Clash", "en")
internal class Mangaclash(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGACLASH, "mangaclash.com", pageSize = 18) {
	override val datePattern = "MM/dd/yyyy"
}
