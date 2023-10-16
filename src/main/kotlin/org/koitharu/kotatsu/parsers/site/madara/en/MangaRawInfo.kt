package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGARAWINFO", "Manga-Raw .Info", "en")
internal class MangaRawInfo(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGARAWINFO, "manga-raw.info", 20) {
	override val postReq = true
}
