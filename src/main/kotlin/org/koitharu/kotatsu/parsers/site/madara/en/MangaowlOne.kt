package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAOWL_ONE", "Manga Owl .One", "en", ContentType.HENTAI)
internal class MangaowlOne(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAOWL_ONE, "mangaowl.one") {
	override val postReq = true
}
