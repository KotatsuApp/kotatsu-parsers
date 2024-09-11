package org.koitharu.kotatsu.parsers.site.madara.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("KDTSCANS", "KdtScans", "")
internal class KdtScans(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.KDTSCANS, "kdtscans.com", 10) {
	override val sourceLocale: Locale = Locale("es")
}
