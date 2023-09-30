package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("COLORED_MANGA", "Colored Manga", "en")
internal class ColoredManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.COLORED_MANGA, "coloredmanga.com") {
	override val datePattern = "dd-MMM"
}
