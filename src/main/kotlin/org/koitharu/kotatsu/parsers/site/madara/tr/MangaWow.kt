package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAWOW", "Manga Wow", "tr")
internal class MangaWow(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAWOW, "mangawow.com", 18) {
	override val datePattern = "d MMMM yyyy"
}
