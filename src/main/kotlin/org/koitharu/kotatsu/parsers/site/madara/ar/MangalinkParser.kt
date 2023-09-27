package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALINK_AR", "Manga Link", "ar")
internal class MangalinkParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALINK_AR, "mangalink.online", pageSize = 10) {
	override val listUrl = "series/"
}
