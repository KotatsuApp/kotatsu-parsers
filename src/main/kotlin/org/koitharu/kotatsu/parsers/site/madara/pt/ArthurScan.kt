package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale


@MangaSourceParser("ARTHUR_SCAN", "Arthur Scan", "pt")
internal class ArthurScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ARTHUR_SCAN, "arthurscan.xyz") {

	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale("pt", "PT")

}
