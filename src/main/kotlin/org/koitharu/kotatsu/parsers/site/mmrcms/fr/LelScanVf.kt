package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.Locale

@MangaSourceParser("LELSCANVF", "Lel Scan Vf", "fr")
internal class LelScanVf(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.LELSCANVF, "lelscanvf.cc") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
