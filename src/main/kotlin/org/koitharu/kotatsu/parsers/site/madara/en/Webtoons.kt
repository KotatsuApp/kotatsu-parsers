package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WEBTOONS", "Webtoons", "en", ContentType.HENTAI)
internal class Webtoons(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WEBTOONS, "webtoons.top", 20) {

	override val listUrl = "read/"
	override val postreq = true
}
