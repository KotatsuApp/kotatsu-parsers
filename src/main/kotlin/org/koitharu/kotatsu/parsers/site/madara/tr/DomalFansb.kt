package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("DOMALFANSB", "Domal Fansub", "tr")
internal class DomalFansb(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.DOMALFANSB, "domalfansb.com") {
	override val datePattern = "d MMMM yyyy"
	override val tagPrefix = "manga-turleri/"
}
