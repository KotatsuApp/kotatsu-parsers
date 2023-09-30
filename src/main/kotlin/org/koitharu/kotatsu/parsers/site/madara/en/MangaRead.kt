package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAREAD", "Manga Read", "en")
internal class MangaRead(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAREAD, "www.mangaread.org") {
	override val tagPrefix = "genres/"
	override val datePattern = "dd.MM.yyyy"
	override val withoutAjax = true
}
