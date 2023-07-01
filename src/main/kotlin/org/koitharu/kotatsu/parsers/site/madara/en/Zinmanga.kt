package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZINMANGA", "ZINMANGA", "en")
internal class Zinmanga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.ZINMANGA, "zinmanga.com") {
	override val datePattern = "MM/dd/yyyy"
}
