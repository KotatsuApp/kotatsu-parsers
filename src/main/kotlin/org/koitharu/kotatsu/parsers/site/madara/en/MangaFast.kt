package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAFAST", "Manga Fast", "en")
internal class MangaFast(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAFAST, "manga-fast.com") {

	override val datePattern = "d MMMM، yyyy"
}
