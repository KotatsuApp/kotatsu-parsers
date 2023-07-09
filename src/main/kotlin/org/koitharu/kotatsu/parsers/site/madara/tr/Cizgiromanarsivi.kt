package org.koitharu.kotatsu.parsers.site.madara.tr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("CIZGIROMANARSIVI", "Cizgiromanarsivi", "tr")
internal class Cizgiromanarsivi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.CIZGIROMANARSIVI, "cizgiromanarsivi.com", 24) {

	override val stylepage = ""
	override val tagPrefix = "kategori/"
	override val datePattern = "dd.MM.yyyy"


}
