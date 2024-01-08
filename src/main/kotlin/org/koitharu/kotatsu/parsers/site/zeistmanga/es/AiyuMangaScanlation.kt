package org.koitharu.kotatsu.parsers.site.zeistmanga.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("AIYUMANGASCANLATION", "AiyuManhua", "es")
internal class AiyuMangaScanlation(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaSource.AIYUMANGASCANLATION, "www.aiyumanhua.com") {
	override val selectPage = "article.chapter img"
}
