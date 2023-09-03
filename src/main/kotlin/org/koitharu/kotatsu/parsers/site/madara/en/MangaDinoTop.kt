package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGADINOTOP", "MangaDino Top", "en")
internal class MangaDinoTop(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGADINOTOP, "mangadino.top", 10) {
	override val postreq = true
}
