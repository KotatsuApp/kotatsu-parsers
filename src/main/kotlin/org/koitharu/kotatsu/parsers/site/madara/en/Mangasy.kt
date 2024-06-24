package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASY", "Mangasy", "en")
internal class Mangasy(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGASY, "www.mangasy.com") {
	override val tagPrefix = "manhua-genre/"
}
