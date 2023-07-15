package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONXYZ", "Webtoon Xyz", "en")
internal class WebtoonXyz(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONXYZ, "www.webtoon.xyz", 20) {

	override val isNsfwSource = true
	override val tagPrefix = "webtoon-genre/"
	override val datePattern = "d MMM yyyy"
}
