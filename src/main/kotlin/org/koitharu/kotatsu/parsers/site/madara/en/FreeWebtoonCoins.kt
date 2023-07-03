package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FREEWEBTOONCOINS", "Free Webtoon Coins", "en")
internal class FreeWebtoonCoins(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FREEWEBTOONCOINS, "freewebtooncoins.com") {

	override val datePattern = "MMMM d, yyyy"
	override val tagPrefix = "webtoon-genre/"
}
