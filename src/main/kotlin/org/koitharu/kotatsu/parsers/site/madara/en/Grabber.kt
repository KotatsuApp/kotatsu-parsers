package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GRABBER", "Grabber", "en")
internal class Grabber(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GRABBER, "grabber.zone", 20) {

	override val tagPrefix = "type/"
	override val datePattern = "dd.MM.yyyy"
}
