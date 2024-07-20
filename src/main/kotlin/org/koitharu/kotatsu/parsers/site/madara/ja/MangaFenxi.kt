package org.koitharu.kotatsu.parsers.site.madara.ja

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("MANGAFENXI", "MangaFenxi", "ja")
internal class MangaFenxi(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAFENXI, "mangafenxi.net", 40) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val tagPrefix = "genres/"
}
