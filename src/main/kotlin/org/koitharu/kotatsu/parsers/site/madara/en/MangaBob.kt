package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGABOB", "MangaBob", "en")
internal class MangaBob(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGABOB, "mangabob.com") {
	override val postReq = true
}
