package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZEROSCAN", "ZeroScan", "pt")
internal class ZeroScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ZEROSCAN, "zeroscan.com.br") {
	override val postReq = true
	override val datePattern: String = "dd/MM/yyyy"
}
