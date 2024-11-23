package org.koitharu.kotatsu.parsers.site.madara.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TRUYENVN", "TruyenVn", "vi", ContentType.HENTAI)
internal class TruyenVn(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TRUYENVN, "truyenvn.vin", 20) {
	override val listUrl = "truyen-tranh/"
	override val tagPrefix = "the-loai/"
	override val datePattern = "dd/MM/yyyy"
}
