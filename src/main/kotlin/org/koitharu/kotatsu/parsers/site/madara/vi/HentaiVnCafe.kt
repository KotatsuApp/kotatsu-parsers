package org.koitharu.kotatsu.parsers.site.madara.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIVNCAFE", "HentaiVnCafe", "vi", ContentType.HENTAI)
internal class HentaiVnCafe(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HENTAIVNCAFE, "hentaivn.cafe", 24) {
	override val listUrl = "truyen-hentai/"
	override val tagPrefix = "the-loai/"
	override val datePattern = "dd/MM/yyyy"
}
