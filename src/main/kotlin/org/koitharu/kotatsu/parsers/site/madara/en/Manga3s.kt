package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_3S", "Manga3s", "en")
internal class Manga3s(context: MangaLoaderContext) : MadaraParser(context, MangaSource.MANGA_3S, "manga3s.com") {
	override val tagPrefix = "manhwa-genre/"
	override val datePattern = "MMMM dd, yyyy"
}
