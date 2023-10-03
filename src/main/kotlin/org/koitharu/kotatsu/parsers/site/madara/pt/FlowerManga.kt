package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FLOWERMANGA", "Flower Manga", "pt")
internal class FlowerManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FLOWERMANGA, "flowermanga.com", 24) {
	override val datePattern = "d MMMM yyyy"
}
