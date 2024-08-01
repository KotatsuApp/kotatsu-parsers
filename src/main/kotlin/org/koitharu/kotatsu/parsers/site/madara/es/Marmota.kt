package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MARMOTA", "Marmota", "es", ContentType.COMICS)
internal class Marmota(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MARMOTA, "marmota.me", 48) {
	override val datePattern = "d 'de' MMMMM 'de' yyyy"
	override val tagPrefix = "genero/"
	override val listUrl = "comic/"
}
