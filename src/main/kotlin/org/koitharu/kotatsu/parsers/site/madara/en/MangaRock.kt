package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAROCK", "MangaRock", "en")
internal class MangaRock(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAROCK, "mangarockteam.com") {

	override val datePattern = "MMMM dd, yyyy"
}
