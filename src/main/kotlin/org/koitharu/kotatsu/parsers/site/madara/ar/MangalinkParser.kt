package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALINK_AR", "MangaLink", "ar")
internal class MangalinkParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALINK_AR, "manga-link.org", pageSize = 10) {
	override val listUrl = "readcomics/"
}
