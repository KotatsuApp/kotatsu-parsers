package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SHIBAMANGA", "Shiba Manga", "en")
internal class ShibaManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SHIBAMANGA, "shibamanga.com") {

	override val datePattern = "MM/dd/yyyy"
}

