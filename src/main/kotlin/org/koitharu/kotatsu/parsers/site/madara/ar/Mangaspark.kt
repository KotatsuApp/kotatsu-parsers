package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASPARK", "Mangaspark", "ar")
internal class Mangaspark(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASPARK, "mangaspark.com", pageSize = 10) {

	override val postreq = true
	override val datePattern = "d MMMM، yyyy"
}
