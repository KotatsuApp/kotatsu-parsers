package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAHENTAI", "MangaHentai", "en", ContentType.HENTAI)
internal class MangaHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAHENTAI, "mangahentai.me", 20) {

	override val tagPrefix = "manga-hentai-genre/"
	override val listUrl = "manga-hentai/"
}
