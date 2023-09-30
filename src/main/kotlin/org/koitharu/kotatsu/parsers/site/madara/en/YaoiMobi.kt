package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YAOIMOBI", "Yaoi .Mobi", "en", ContentType.HENTAI)
internal class YaoiMobi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.YAOIMOBI, "yaoi.mobi") {
	override val postreq = true
}
