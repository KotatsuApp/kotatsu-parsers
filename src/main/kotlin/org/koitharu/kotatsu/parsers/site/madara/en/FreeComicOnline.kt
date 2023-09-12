package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("FREECOMICONLINE", "Free Comic Online", "en", ContentType.HENTAI)
internal class FreeComicOnline(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.FREECOMICONLINE, "freecomiconline.me") {
	override val postreq = true
	override val listUrl = "comic/"
	override val tagPrefix = "comic-genre/"
}
