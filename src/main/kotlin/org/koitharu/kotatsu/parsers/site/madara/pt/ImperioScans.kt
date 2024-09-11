package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("IMPERIOSCANS", "ImperioScans", "pt")
internal class ImperioScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.IMPERIOSCANS, "imperioscans.com.br") {
	override val datePattern: String = "dd/MM/yyyy"
}
