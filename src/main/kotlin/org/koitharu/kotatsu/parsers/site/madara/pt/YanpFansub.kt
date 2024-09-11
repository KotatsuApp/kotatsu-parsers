package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YANPFANSUB", "YanpFansub", "pt")
internal class YanpFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.YANPFANSUB, "trisalyanp.com") {
	override val datePattern = "d 'de' MMMM 'de' yyyy"
}
