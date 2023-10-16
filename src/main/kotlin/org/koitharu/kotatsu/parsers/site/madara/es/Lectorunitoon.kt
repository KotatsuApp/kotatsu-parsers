package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LECTORUNITOON", "Lectoruni Toon", "es")
internal class Lectorunitoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LECTORUNITOON, "lectorunitoon.com", 10) {
	override val tagPrefix = "generos/"
	override val datePattern = "dd/MM/yyyy"
}
