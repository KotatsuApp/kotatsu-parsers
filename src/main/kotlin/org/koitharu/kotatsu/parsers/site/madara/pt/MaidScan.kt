package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MAIDSCAN", "MaidScan", "pt", ContentType.HENTAI)
internal class MaidScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MAIDSCAN, "empreguetes.site", 10) {
	override val datePattern: String = "dd/MM/yyyy"
}
