package org.koitharu.kotatsu.parsers.site.madara.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("EROMANHWA", "EroManhwa", "", ContentType.HENTAI)
internal class EroManhwa(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.EROMANHWA, "eromanhwa.org") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
