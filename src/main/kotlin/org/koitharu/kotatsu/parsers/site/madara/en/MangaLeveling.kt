package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEVELING", "Manga Leveling", "en")
internal class MangaLeveling(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALEVELING, "mangaleveling.com", 30) {

	override val postreq = true
	override val tagPrefix = "comics-genre/"
	override val datePattern = "MM/dd/yyyy"

}
