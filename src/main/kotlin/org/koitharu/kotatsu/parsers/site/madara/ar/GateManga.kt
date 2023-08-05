package org.koitharu.kotatsu.parsers.site.madara.ar


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GATEMANGA", "Gate Manga", "ar")
internal class GateManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GATEMANGA, "gatemanga.com") {

	override val postreq = true
	override val datePattern = "d MMMM، yyyy"
	override val listUrl = "ar/"
}
