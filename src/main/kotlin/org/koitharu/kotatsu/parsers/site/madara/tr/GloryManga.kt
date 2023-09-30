package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GLORYMANGA", "Glory Manga", "tr")
internal class GloryManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GLORYMANGA, "glorymanga.com", 18) {
	override val datePattern = "dd/MM/yyyy"
}
