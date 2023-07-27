package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA_MANHUA", "Manga Manhua", "en")
internal class MangaManhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGA_MANHUA, "mangamanhua.online", pageSize = 10) {
	override val datePattern = "d MMMM، yyyy"
}
