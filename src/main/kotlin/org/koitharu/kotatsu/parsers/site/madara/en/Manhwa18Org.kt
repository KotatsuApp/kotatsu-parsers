package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWA18ORG", "Manhwa18.org", "en", ContentType.HENTAI)
internal class Manhwa18Org(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHWA18ORG, "manhwa18.org") {
	override val postReq = true
}
