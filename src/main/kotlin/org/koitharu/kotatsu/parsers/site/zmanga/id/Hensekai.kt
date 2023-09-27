package org.koitharu.kotatsu.parsers.site.zmanga.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zmanga.ZMangaParser
import java.util.Locale

@MangaSourceParser("HENSEKAI", "Hensekai", "id", ContentType.HENTAI)
internal class Hensekai(context: MangaLoaderContext) :
	ZMangaParser(context, MangaSource.HENSEKAI, "hensekai.com") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
