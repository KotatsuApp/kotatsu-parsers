package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BLUESOLO", "Blue Solo", "fr")
internal class BlueSolo(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.BLUESOLO, "www1.bluesolo.org", 10) {
	override val datePattern = "d MMMM yyyy"
}
