package org.koitharu.kotatsu.parsers.site.madara.all

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale

@MangaSourceParser("MANGACRAZY", "MangaCrazy", "", ContentType.HENTAI)
internal class MangaCrazy(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGACRAZY, "mangacrazy.net") {
	override val sourceLocale: Locale = Locale.ENGLISH
}
