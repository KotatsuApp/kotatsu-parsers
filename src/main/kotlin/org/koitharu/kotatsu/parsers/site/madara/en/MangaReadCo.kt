package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAREADCO", "Manga Read Co", "en")
internal class MangaReadCo(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAREADCO, "mangaread.co", 16) {

	override val tagPrefix = "m-genre/"
	override val datePattern = "yyyy-MM-dd"

}
