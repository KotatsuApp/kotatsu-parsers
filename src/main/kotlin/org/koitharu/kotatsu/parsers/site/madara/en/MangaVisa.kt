package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAVISA", "MangaVisa", "en")
internal class MangaVisa(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAVISA, "mangavisa.com") {

	override val withoutAjax = true
}
