package org.koitharu.kotatsu.parsers.site.madara.tr


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale


@MangaSourceParser("ANISA_MANGA", "Anisa Manga", "tr")
internal class AnisaManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ANISA_MANGA, "anisamanga.com") {

	override val datePattern = "MMMM d, yyyy"
	override val sourceLocale: Locale = Locale("tr")
}
