package org.koitharu.kotatsu.parsers.site.mmrcms.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mmrcms.MmrcmsParser
import java.util.Locale

@MangaSourceParser("SCAN_FR_ORG", "Scan-Fr .Org", "fr")
internal class ScanFrOrg(context: MangaLoaderContext) :
	MmrcmsParser(context, MangaSource.SCAN_FR_ORG, "www.scan-fr.org") {

	override val sourceLocale: Locale = Locale.ENGLISH
	override val selectChapter = "ul.chapterszozo li"
}
