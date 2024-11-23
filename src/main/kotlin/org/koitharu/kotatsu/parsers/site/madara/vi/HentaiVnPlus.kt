package org.koitharu.kotatsu.parsers.site.madara.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIVNPLUS", "HentaiVnPlus", "vi", ContentType.HENTAI)
internal class HentaiVnPlus(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HENTAIVNPLUS, "hentaivn.now", 24) {
	override val listUrl = "truyen-hentai/"
	override val tagPrefix = "the-loai/"
	override val datePattern = "dd/MM/yyyy"
}
