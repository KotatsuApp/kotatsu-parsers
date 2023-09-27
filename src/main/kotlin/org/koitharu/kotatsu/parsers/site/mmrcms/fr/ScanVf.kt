package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.Locale

@MangaSourceParser("SCANVF", "Scan Vf", "fr")
internal class ScanVf(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.SCANVF, "www.scan-vf.net") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
