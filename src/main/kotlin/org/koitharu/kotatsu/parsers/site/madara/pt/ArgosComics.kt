package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("ARGOSCOMICS", "ArgosComics", "pt")
internal class ArgosComics(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ARGOSCOMICS, "argoscomics.com.br") {
	override val datePattern: String = "d 'de' MMMM 'de' yyyy"
}
