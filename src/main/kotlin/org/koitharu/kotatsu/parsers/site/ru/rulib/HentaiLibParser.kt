package org.koitharu.kotatsu.parsers.site.ru.rulib

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("HENTAILIB", "HentaiLib", "ru", type = ContentType.HENTAI)
internal class HentaiLibParser(context: MangaLoaderContext) : MangaLibParser(context, MangaSource.HENTAILIB) {

	override val configKeyDomain = ConfigKey.Domain("hentailib.me")
	override fun isNsfw(doc: Document) = true
}
