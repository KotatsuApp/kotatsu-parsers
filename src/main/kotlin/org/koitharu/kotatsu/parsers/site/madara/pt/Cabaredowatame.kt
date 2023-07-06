package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("CABAREDOWATAME", "Dessert Scan", "pt")
internal class Cabaredowatame(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.CABAREDOWATAME, "cabaredowatame.site", 10) {

	override val datePattern = "dd/MM/yyyy"
}
