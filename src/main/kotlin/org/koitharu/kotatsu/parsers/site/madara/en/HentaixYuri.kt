package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIXYURI", "HentaiXYuri", "en", ContentType.HENTAI)
internal class HentaixYuri(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.HENTAIXYURI, "hentaixyuri.com", 16) {
	override val postReq = true
}
