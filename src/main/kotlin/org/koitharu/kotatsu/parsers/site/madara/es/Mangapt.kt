package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAPT", "Mangapt", "es")
internal class Mangapt(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAPT, "mangapt.com") {

	override val datePattern = "dd/MM/yyyy"
}
