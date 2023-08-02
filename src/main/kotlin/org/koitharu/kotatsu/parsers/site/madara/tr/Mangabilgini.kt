package org.koitharu.kotatsu.parsers.site.madara.tr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("MANGABILGINI", "Mangabilgini", "tr")
internal class Mangabilgini(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGABILGINI, "mangabilgini.com", 44) {

	override val selectDesc = "div.ozet__icerik"
	override val postreq = true
	override val datePattern = "d MMMM yyyy"
}
