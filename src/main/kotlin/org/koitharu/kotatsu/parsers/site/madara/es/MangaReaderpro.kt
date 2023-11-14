package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAREADERPRO", "MangaReaderPro", "es")
internal class MangaReaderpro(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAREADERPRO, "mangareaderpro.com", 10) {
	override val datePattern = "MM/dd/yyyy"
}
