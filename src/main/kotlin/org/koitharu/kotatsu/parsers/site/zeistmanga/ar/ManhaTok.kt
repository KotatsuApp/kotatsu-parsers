package org.koitharu.kotatsu.parsers.site.zeistmanga.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("MANHATOK", "ManhaTok", "ar")
internal class ManhaTok(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.MANHATOK, "manhatok.blogspot.com") {
	override val selectPage = "#seoneurons-target img"
}
