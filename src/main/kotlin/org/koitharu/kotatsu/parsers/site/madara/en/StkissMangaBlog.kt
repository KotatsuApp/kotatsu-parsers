package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("STKISSMANGABLOG", "St kiss Manga .Blog", "en")
internal class StkissMangaBlog(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.STKISSMANGABLOG, "1stkissmanga.blog", 10) {
	override val postReq = true
}
