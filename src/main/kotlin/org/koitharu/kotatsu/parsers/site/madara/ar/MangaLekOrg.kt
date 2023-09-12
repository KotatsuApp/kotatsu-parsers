package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEK_ORG", "MangaLek Org", "ar")
internal class MangaLekOrg(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGALEK_ORG, "ww.mangalek.org", pageSize = 10) {
	override val listUrl = "comics/"
	override val datePattern = "dd-MM-yyyy"
}
