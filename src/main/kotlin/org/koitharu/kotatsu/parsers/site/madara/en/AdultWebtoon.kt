package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("ADULT_WEBTOON", "Adult Webtoon", "en")
internal class AdultWebtoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ADULT_WEBTOON, "adultwebtoon.com") {

	override val isNsfwSource = true
	override val tagPrefix = "adult-webtoon-genre/"
	override val postreq = true
}
