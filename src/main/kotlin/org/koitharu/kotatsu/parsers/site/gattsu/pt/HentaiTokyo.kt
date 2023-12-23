package org.koitharu.kotatsu.parsers.site.gattsu.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.gattsu.GattsuParser

@MangaSourceParser("HENTAITOKYO", "HentaiTokyo", type = ContentType.HENTAI)
internal class HentaiTokyo(context: MangaLoaderContext) :
	GattsuParser(context, MangaSource.HENTAITOKYO, "hentaitokyo.net") {
	override val tagUrl = "tags"
}
