package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGACLASH_TV", "MangaClash.tv", "en")
internal class MangaClashTv(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGACLASH_TV, "mangaclash.tv", pageSize = 10) {
	override val datePattern = "MM/dd/yyyy"
	override val postReq = true
}
