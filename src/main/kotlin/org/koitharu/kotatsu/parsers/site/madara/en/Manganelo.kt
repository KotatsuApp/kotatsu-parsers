package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGANELO", "MangaNelo.biz", "en")
internal class Manganelo(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGANELO, "manganelo.biz", 10) {
	override val postReq = true
}
