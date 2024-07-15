package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("CAFECOMYAOI", "CafecomYaoi", "pt", ContentType.HENTAI)
internal class CafecomYaoi(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.CAFECOMYAOI, "cafecomyaoi.com.br") {
	override val datePattern = "dd/MM/yyyy"
}
