package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_SCANTRAD", "MangaScantrad.io", "fr")
internal class MangaScantrad(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGA_SCANTRAD, "manga-scantrad.io") {
	override val datePattern = "d MMMM yyyy"
}
