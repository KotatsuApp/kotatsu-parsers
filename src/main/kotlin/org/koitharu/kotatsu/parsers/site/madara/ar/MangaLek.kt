package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEK", "MangaLek", "ar")
internal class MangaLek(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALEK, "mangalek.com", pageSize = 20) {
	override val datePattern = "dd-MM-yyyy"
}
