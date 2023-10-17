package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NIVERAFANSUB", "NiveraFansub", "tr", ContentType.HENTAI)
internal class NiveraFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NIVERAFANSUB, "niverafansub.co") {
	override val datePattern = "d MMMM yyyy"
}
