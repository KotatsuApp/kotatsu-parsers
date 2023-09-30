package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ESOMANGA", "Eso Manga", "tr")
internal class EsoManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ESOMANGA, "esomanga.com", 10) {
	override val postreq = true
	override val datePattern = "dd/MM/yyyy"
	override val tagPrefix = "manga-kategoriler/"
}
