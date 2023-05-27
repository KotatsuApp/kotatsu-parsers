package org.koitharu.kotatsu.parsers.site.rulib

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

@MangaSourceParser("YAOILIB", "YaoiLib", "ru")
internal class YaoiLibParser(context: MangaLoaderContext) : MangaLibParser(context, MangaSource.YAOILIB) {

	override val configKeyDomain = ConfigKey.Domain("yaoilib.me")
	override fun isNsfw(doc: Document) = true
}
