package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.*

@MangaSourceParser("KALANGO", "Kalango", "pt")
internal class Kalango(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.KALANGO, "kalango.org") {
	override val datePattern: String = "dd 'de' MMMM 'de' yyyy"
	override val sourceLocale: Locale = Locale.ENGLISH
}
