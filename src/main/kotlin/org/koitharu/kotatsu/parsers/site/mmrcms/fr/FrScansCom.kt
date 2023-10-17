package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.Locale

@MangaSourceParser("FRSCANSCOM", "FrScans.com", "fr")
internal class FrScansCom(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.FRSCANSCOM, "frscans.com") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
