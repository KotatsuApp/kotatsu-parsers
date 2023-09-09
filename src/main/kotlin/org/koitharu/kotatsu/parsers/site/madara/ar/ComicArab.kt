package org.koitharu.kotatsu.parsers.site.madara.ar


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("COMICARAB", "ComicArab", "ar")
internal class ComicArab(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.COMICARAB, "comicarab.com", pageSize = 24) {
	override val datePattern = "d MMMM، yyyy"
}
