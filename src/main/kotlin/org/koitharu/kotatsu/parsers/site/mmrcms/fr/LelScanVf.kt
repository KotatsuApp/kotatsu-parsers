package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.*

@MangaSourceParser("LELSCANVF", "LelScanVf", "fr")
internal class LelScanVf(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaParserSource.LELSCANVF, "lelscanvf.cc") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
