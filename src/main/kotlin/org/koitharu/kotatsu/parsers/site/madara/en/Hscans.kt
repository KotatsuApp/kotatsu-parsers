package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HSCANS", "Hscans", "en")
internal class Hscans(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HSCANS, "hscans.com", 10) {

	override val datePattern = "dd/MM/yyyy"

}
