package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONTR", "Webtoontr", "tr")
internal class Webtoontr(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONTR, "webtoon-tr.com", 16) {

	override val tagPrefix = "webtoon-kategori/"
	override val datePattern = "dd/MM/yyyy"
}
