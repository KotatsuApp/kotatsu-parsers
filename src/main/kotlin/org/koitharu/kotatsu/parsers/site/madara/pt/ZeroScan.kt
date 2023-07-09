package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZEROSCAN", "Zero Scan", "pt")
internal class ZeroScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ZEROSCAN, "zeroscan.com.br") {

	override val postreq = true
	override val datePattern: String = "dd/MM/yyyy"
}
