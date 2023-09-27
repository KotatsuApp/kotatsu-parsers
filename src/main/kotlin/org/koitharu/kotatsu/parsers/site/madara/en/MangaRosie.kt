package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAROSIE", "Manga Rosie", "en")
internal class MangaRosie(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAROSIE, "mangarosie.in", pageSize = 16) {
	override val datePattern = "MMMM dd, yyyy"
}
