package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MONOMANGA", "Mono Manga", "tr")
internal class MonoManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MONOMANGA, "monomanga.com") {
	override val datePattern = "d MMM yyyy"
}
