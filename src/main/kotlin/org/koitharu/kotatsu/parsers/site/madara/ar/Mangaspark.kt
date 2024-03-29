package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASPARK", "MangaSpark", "ar")
internal class Mangaspark(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASPARK, "mangaspark.org", pageSize = 10) {
	override val postReq = true
	override val datePattern = "d MMMM، yyyy"
}
