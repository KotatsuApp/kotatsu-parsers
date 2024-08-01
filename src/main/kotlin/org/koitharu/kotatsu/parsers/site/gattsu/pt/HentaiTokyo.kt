package org.koitharu.kotatsu.parsers.site.gattsu.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.gattsu.GattsuParser

@MangaSourceParser("HENTAITOKYO", "HentaiTokyo", "pt", ContentType.HENTAI)
internal class HentaiTokyo(context: MangaLoaderContext) :
	GattsuParser(context, MangaParserSource.HENTAITOKYO, "hentaitokyo.net") {
	override val tagUrl = "tags"
}
