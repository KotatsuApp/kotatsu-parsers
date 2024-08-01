package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.*

@Broken
@MangaSourceParser("FRSCANSCOM", "FrScans.com", "fr")
internal class FrScansCom(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaParserSource.FRSCANSCOM, "frscans.com") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
