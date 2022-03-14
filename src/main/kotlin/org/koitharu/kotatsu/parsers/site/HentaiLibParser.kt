package org.koitharu.kotatsu.parsers.site

import org.jsoup.nodes.Document
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.model.MangaSource

internal class HentaiLibParser(context: MangaLoaderContext) : MangaLibParser(context) {

	override val defaultDomain = "hentailib.me"

	override val source = MangaSource.HENTAILIB

	override fun isNsfw(doc: Document) = true
}