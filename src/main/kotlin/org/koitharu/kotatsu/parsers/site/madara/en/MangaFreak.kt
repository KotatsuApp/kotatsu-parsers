package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAFREAK", "Manga Freak", "en")
internal class MangaFreak(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAFREAK, "mangafreak.online") {
	override val postReq = true
	override val datePattern = "dd MMMM، yyyy"
}
