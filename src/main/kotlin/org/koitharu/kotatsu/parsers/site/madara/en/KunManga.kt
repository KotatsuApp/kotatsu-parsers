package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KUNMANGA", "Kun Manga", "en")
internal class KunManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KUNMANGA, "kunmanga.com", 10) {

	override val datePattern = "MMMM d, yyyy"

}
