package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAKAKALOT_IO", "Mangakakalot Io", "en")
internal class MangakakalotIo(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAKAKALOT_IO, "mangakakalot.io", 20) {
	override val postreq = true
}
