package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FREEMANGA", "Free Manga", "en")
internal class FreeManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.FREEMANGA, "freemanga.me") {
	override val datePattern = "MMMM dd, yyyy"
}
