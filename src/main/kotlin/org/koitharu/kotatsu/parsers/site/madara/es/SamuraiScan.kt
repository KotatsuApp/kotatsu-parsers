package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("SAMURAISCAN", "Samurai Scan", "es")
internal class SamuraiScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.SAMURAISCAN, "samuraiscan.com", 10) {

	override val datePattern = "dd/MM/yyyy"
}
