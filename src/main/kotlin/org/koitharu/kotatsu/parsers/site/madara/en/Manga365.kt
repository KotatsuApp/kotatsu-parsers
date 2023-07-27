package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_365", "365Manga", "en")
internal class Manga365(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_365, "365manga.com") {
	override val datePattern = "MMMM dd, yyyy"
}
