package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("MANGAEFFECT", "MangaEffect", "en")
internal class MangaEffect(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAEFFECT, "mangaeffect.com") {
	override val datePattern = "dd.MM.yyyy"
	override val withoutAjax = true
}
