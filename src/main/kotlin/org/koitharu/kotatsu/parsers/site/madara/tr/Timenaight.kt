package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("TIMENAIGHT", "TimeNaight", "tr")
internal class Timenaight(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.TIMENAIGHT, "timenaight.com") {
	override val postReq = true
	override val datePattern = "dd/MM/yyyy"
}
