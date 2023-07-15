package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FREEMANGA", "FreeManga", "en")
internal class FreeManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.FREEMANGA, "freemanga.me") {
	override val datePattern = "MMMM dd, yyyy"
}
