package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOON", "Webtoon Uk", "en")
internal class Webtoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOON, "webtoon.uk", 20) {

	override val tagPrefix = "manhwa-genre/"
	override val postreq = true
}
