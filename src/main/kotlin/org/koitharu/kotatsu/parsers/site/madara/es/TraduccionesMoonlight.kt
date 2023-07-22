package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("TRADUCCIONESMOONLIGHT", "Traducciones Moonlight", "es")
internal class TraduccionesMoonlight(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TRADUCCIONESMOONLIGHT, "traduccionesmoonlight.com") {

	override val datePattern = "d MMMM, yyyy"
}
