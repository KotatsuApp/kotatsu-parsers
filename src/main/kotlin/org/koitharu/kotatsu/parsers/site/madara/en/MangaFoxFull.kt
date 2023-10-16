package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAFOXFULL", "Manga Fox Full", "en")
internal class MangaFoxFull(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAFOXFULL, "mangafoxfull.com") {
	override val postReq = true
}
