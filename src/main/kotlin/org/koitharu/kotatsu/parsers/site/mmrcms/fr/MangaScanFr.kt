package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.*

@MangaSourceParser("MANGASCANFR", "MangaScanFr", "fr")
internal class MangaScanFr(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaParserSource.MANGASCANFR, "mangascan-fr.net") {
	override val sourceLocale: Locale = Locale.ENGLISH
	override val imgUpdated = ".jpg"
}
