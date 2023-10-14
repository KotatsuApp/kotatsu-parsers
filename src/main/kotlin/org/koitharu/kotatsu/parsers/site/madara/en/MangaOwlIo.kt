package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAOWL_IO", "Manga Owl .Io", "en")
internal class MangaOwlIo(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAOWL_IO, "mangaowl.io") {
	override val listUrl = "mangaowl-all/"
}
