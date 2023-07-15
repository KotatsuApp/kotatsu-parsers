package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIWEBTOON", "Hentai Webtoon", "en")
internal class HentaiWebtoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAIWEBTOON, "hentaiwebtoon.com") {

	override val isNsfwSource = true
	override val postreq = true

}
