package org.koitharu.kotatsu.parsers.site.zmanga.id

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zmanga.ZMangaParser

@Broken // redirect to @MANGASUSUKU
@MangaSourceParser("KOMIKINDO_INFO", "KomikIndo.info", "id", ContentType.HENTAI)
internal class KomikIndoInfo(context: MangaLoaderContext) :
	ZMangaParser(context, MangaParserSource.KOMIKINDO_INFO, "mangasusuku.com") {
	override val datePattern = "dd MMM yyyy"
}
