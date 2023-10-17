package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BIBIMANGA", "BibiManga", "en", ContentType.HENTAI)
internal class BibiManga(context: MangaLoaderContext) : MadaraParser(context, MangaSource.BIBIMANGA, "bibimanga.com") {
	override val datePattern = "MMMM dd, yyyy"
}
