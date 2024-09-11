package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("YONABAR", "YonaBar", "ar")
internal class YonaBar(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.YONABAR, "yonabar.xyz", 10) {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val listUrl = "yaoi/"
}
