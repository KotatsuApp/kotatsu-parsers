package org.koitharu.kotatsu.parsers.site.madara.it

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BEYONDTHEATARAXIA", "Beyondtheataraxia", "it")
internal class Beyondtheataraxia(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BEYONDTHEATARAXIA, "www.beyondtheataraxia.com", 10) {
	override val datePattern = "d MMMM yyyy"
}
