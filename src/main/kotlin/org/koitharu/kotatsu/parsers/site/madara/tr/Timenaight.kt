package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TIMENAIGHT", "Timenaight", "tr")
internal class Timenaight(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TIMENAIGHT, "timenaight.com") {
	override val postReq = true
	override val datePattern = "dd/MM/yyyy"
}
