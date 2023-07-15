package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAHENTAI", "Manga Hentai", "en")
internal class MangaHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAHENTAI, "mangahentai.me", 20) {

	override val isNsfwSource = true
	override val tagPrefix = "manga-hentai-genre/"
}
