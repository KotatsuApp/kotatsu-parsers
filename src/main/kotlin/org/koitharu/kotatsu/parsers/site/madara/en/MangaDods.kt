package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGADODS", "MangaDods", "en")
internal class MangaDods(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGADODS, "www.mangadods.com") {

	override val selectDesc = "div.description-summary"
	override val datePattern = "dd-MMM"
}
