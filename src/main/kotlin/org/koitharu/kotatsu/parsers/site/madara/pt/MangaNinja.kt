package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGANINJA", "MangaNinja", "pt")
internal class MangaNinja(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGANINJA, "manganinja.com", 10) {
	override val datePattern: String = "dd/MM/yyyy"
}
