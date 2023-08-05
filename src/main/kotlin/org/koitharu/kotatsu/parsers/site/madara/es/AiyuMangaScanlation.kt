package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("AIYUMANGASCANLATION", "Aiyu Manga", "es")
internal class AiyuMangaScanlation(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.AIYUMANGASCANLATION, "aiyumanga.com") {
	override val datePattern = "MM/dd/yyyy"
	override val listUrl = "series/"
}
