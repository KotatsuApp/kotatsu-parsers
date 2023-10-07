package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_LEK", "Manga-Lek", "ar")
internal class Manga_Lek(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGA_LEK, "manga-lek.com") {
	override val listUrl = "mangalek/"
	override val postReq = true
}
