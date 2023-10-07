package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("AKUMANOTENSHI", "AkumanoTenshi", "pt")
internal class AkumanoTenshi(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.AKUMANOTENSHI, "akumanotenshi.com", 48) {
	override val listUrl = "series/"
	override val tagPrefix = "series-genre/"
	override val datePattern = "dd/MM/yyyy"
	override val postReq = true
}
