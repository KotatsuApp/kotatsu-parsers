package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ALLPORN_COMIC", "AllPornComic", "en", ContentType.HENTAI)
internal class AllPornComic(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.ALLPORN_COMIC, "allporncomic.com", pageSize = 24) {
	override val tagPrefix = "porncomic-genre/"
	override val datePattern = "MMMM dd, yyyy"
}
