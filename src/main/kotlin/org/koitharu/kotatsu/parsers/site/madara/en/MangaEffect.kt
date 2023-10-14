package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAEFFECT", "Manga Effect", "en")
internal class MangaEffect(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAEFFECT, "mangaeffect.com") {
	override val datePattern = "dd.MM.yyyy"
	override val withoutAjax = true
}
