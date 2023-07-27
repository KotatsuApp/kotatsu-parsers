package org.koitharu.kotatsu.parsers.site.madara.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAUPTOCATS", "Manga Uptocats", "th")
internal class MangaUptocats(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAUPTOCATS, "manga-uptocats.com") {

	override val datePattern: String = "d MMMM yyyy"
}
