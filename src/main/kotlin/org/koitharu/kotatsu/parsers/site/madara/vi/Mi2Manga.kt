package org.koitharu.kotatsu.parsers.site.madara.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MI2MANGA", "Mi2Manga", "vi")
internal class Mi2Manga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MI2MANGA, "www.mi2manga2.com") {
	override val listUrl = "truyen-tranh/"
	override val tagPrefix = "the-loai/"
	override val datePattern = "d MMMM, yyyy"
}
