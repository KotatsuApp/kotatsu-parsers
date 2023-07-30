package org.koitharu.kotatsu.parsers.site.zmanga.id


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zmanga.ZMangaParser


@MangaSourceParser("KOMIKINDO_INFO", "KomikIndo Info", "id", ContentType.HENTAI)
internal class KomikIndoInfo(context: MangaLoaderContext) :
	ZMangaParser(context, MangaSource.KOMIKINDO_INFO, "komikindo.info") {

	override val datePattern = "dd MMM yyyy"

}
