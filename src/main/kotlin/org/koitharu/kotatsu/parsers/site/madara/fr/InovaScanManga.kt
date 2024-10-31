package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("INOVASCANMANGA", "InovaScanManga", "fr")
internal class InovaScanManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.INOVASCANMANGA, "inovascanmanga.com") {
	override val datePattern = "d MMMM yyyy"
}
