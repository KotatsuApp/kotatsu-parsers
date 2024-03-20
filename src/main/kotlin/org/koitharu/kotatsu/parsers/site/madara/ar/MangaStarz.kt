package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASTARZ", "MangaStarz", "ar")
internal class MangaStarz(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASTARZ, "manga-starz.com", pageSize = 10) {
	override val datePattern = "d MMMM، yyyy"
}
