package org.koitharu.kotatsu.parsers.site.hotcomics.zh

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.hotcomics.HotComicsParser

@MangaSourceParser("TOOMICSTC", "TooMicsTc", "zh")
internal class TooMicsTc(context: MangaLoaderContext) :
	HotComicsParser(context, MangaParserSource.TOOMICSTC, "toomics.com/tc") {
	override val isSearchSupported = false
	override val mangasUrl = "/webtoon/ranking/genre"
	override val selectMangas = "li > div.visual"
	override val selectMangaChapters = "li.normal_ep:has(.coin-type1)"
	override val selectTagsList = "div.genre_list li:not(.on) a"
	override val selectPages = "div[id^=load_image_] img"
	override val onePage = true
}
