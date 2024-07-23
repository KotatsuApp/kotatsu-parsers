package org.koitharu.kotatsu.parsers.site.zeistmanga.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@Broken
@MangaSourceParser("ZSCANLATION", "ZScanlation", "pt", ContentType.HENTAI)
internal class ZScanlation(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.ZSCANLATION, "www.zscanlation.com") {
	override val sateOngoing: String = "Em Lançamento"
	override val sateFinished: String = "Completo"
	override val sateAbandoned: String = "Dropado"
}
