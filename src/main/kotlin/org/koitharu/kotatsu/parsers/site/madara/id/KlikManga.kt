package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KLIKMANGA", "KlikManga", "id", ContentType.HENTAI)
internal class KlikManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.KLIKMANGA, "klikmanga.id", 36) {
	override val tagPrefix = "genre/"
	override val datePattern = "MMM d, yyyy"
}
