package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEKS", "MangaLeks", "ar")
internal class MangaLeks(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALEKS, "mangaleks.com") {

	override val datePattern = "yyyy/MM/dd"
	override val postreq = true
}
