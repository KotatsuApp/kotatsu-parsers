package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("STKISSMANGA_TV", "1stKissManga Tv", "en")
internal class StkissMangaTv(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.STKISSMANGA_TV, "1stkissmanga.tv", 20) {
	override val postreq = true
}
