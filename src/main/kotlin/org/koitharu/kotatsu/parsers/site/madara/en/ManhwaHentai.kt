package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWAHENTAI", "Manhwa Hentai", "en", ContentType.HENTAI)
internal class ManhwaHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAHENTAI, "manhwahentai.me", 20) {

	override val tagPrefix = "webtoon-genre/"
	override val listUrl = "webtoon/"
}
