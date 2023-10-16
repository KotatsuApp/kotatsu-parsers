package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIXCOMIC", "Hentai x Comic", "en", ContentType.HENTAI)
internal class HentaixComic(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAIXCOMIC, "hentaixcomic.com", 16) {
	override val postReq = true
}
