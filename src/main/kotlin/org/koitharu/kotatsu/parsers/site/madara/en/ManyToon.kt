package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANYTOON", "Many Toon", "en", ContentType.HENTAI)
internal class ManyToon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANYTOON, "manytoon.com", 20) {
	override val listUrl = "comic/"
}
