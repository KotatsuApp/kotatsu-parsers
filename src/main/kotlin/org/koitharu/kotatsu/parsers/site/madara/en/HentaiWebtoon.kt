package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIWEBTOON", "HentaiWebtoon", "en", ContentType.HENTAI)
internal class HentaiWebtoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAIWEBTOON, "hentaiwebtoon.com") {
	override val postReq = true
}
