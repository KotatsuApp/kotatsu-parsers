package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("BOYS_LOVE", "BoysLove", "en", ContentType.HENTAI)
internal class BoysLove(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.BOYS_LOVE, "boyslove.me", 20) {
	override val tagPrefix = "boyslove-genre/"
	override val listUrl = "boyslove/"
	override val postReq = true
}
