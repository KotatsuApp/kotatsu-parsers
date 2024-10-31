package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PLUMACOMICS", "PlumaComics", "pt")
internal class PlumaComics(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.PLUMACOMICS, "plumacomics.cloud") {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
