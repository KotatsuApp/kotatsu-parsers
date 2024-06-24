package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("APENASMAISUMYAOI", "Apenasmaisum Yaoi", "pt", ContentType.HENTAI)
internal class ApenasmaisumYaoi(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.APENASMAISUMYAOI, "apenasmaisumyaoi.com") {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
