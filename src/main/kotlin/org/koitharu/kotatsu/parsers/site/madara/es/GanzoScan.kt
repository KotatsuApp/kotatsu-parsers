package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GANZOSCAN", "GanzoScan", "es")
internal class GanzoScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GANZOSCAN, "ganzoscan.com") {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
