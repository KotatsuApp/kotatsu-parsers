package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGACV", "Manga Cv", "en")
internal class MangaCv(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGACV, "mangacv.com", pageSize = 10) {
	override val datePattern = "MMMM dd, yyyy"
}
