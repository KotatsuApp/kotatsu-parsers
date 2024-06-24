package org.koitharu.kotatsu.parsers.site.manga18.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.manga18.Manga18Parser

@MangaSourceParser("TUMANHWAS", "Tumanhwas", "es", ContentType.HENTAI)
internal class Tumanhwas(context: MangaLoaderContext) :
	Manga18Parser(context, MangaParserSource.TUMANHWAS, "tumanhwas.club") {
	override val selectTag = "div.item:contains(Géneros) div.info_value a"
	override val selectAlt = "div.item:contains(Títulos alternativos) div.info_value"
}
