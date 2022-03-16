package org.koitharu.kotatsu.parsers.site

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class HentaiLibParser(context: MangaLoaderContext) : MangaLibParser(context, MangaSource.HENTAILIB) {

	override val configKeyDomain = ConfigKey.Domain("hentailib.me", null)
	override fun isNsfw(doc: Document) = true
}