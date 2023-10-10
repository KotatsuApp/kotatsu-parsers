package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TABERU", "Taberu", "pt")
internal class Taberu(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TABERU, "taberu.org", 10) {
	override val datePattern: String = "dd/MM/yyyy"
}
