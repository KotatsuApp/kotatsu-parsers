package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWAHENTAI", "Manhwa Hentai", "en")
internal class ManhwaHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWAHENTAI, "manhwahentai.me", 20) {

	override val isNsfwSource = true
	override val tagPrefix = "webtoon-genre/"
}
