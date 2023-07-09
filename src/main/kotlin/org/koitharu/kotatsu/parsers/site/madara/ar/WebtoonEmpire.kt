package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONEMPIRE", "Webtoon Empire", "ar")
internal class WebtoonEmpire(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONEMPIRE, "webtoonempire.org", pageSize = 10) {

	override val datePattern = "d MMMM yyyy"
}
