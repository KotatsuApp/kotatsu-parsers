package org.koitharu.kotatsu.parsers.site.madara.zh

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BAKAMH", "Bakamh", "zh")
internal class Bakamh(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.BAKAMH, "bakamh.com") {
	override val datePattern = "YYYY 年 M 月 d 日"
}
