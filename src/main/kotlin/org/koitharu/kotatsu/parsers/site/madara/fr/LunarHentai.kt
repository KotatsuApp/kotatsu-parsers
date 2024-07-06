package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("LUNARHENTAI", "LunarHentai", "fr", ContentType.HENTAI)
internal class LunarHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LUNARHENTAI, "hentai.lunarscans.fr") {
	override val datePattern = "dd MMMM yyyy"
}
