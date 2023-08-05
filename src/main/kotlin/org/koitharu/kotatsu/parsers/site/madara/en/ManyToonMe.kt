package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANYTOONME", "Many Toon Me", "en", ContentType.HENTAI)
internal class ManyToonMe(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANYTOONME, "manytoon.me", 20) {
	override val listUrl = "manhwa/"
	override val tagPrefix = "manhwa-genre/"
}
