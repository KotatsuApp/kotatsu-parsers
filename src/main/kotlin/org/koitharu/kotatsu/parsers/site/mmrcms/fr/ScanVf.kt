package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.*

@MangaSourceParser("SCANVF", "ScanVf", "fr")
internal class ScanVf(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaParserSource.SCANVF, "www.scan-vf.net") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
