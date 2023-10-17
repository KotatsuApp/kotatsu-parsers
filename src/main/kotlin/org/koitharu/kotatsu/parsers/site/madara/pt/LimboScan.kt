package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LIMBOSCAN", "LimboScan", "pt")
internal class LimboScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LIMBOSCAN, "limboscan.com.br") {
	override val tagPrefix = "obras-genre/"
	override val listUrl = "obras/"
	override val datePattern: String = "dd/MM/yyyy"
}
