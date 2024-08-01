package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GOOFFANSUB", "GoofFansub", "pt", ContentType.HENTAI)
internal class GoofFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.GOOFFANSUB, "gooffansub.com") {
	override val datePattern: String = "dd/MM/yyyy"
}
