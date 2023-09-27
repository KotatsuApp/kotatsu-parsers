package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZINMANHWA", "Zin Manhwa", "en")
internal class Zinmanhwa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ZINMANHWA, "zinmanhwa.com") {
	override val datePattern = "dd/MM/yyyy"
}
