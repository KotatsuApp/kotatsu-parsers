package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PORTALYAOI", "PortalYaoi", "pt", ContentType.HENTAI)
internal class PortalYaoi(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.PORTALYAOI, "portalyaoi.com", 10) {
	override val tagPrefix = "genero/"
	override val datePattern: String = "dd/MM"
}
