package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASCANTRADUNOFFICIAL", "Manga Scantrad (Unofficial)", "fr")
internal class MangaScantradUnofficial(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASCANTRADUNOFFICIAL, "www.mangascantrad.fr", 10) {

	override val datePattern = "dd/MM/yyyy"
}
