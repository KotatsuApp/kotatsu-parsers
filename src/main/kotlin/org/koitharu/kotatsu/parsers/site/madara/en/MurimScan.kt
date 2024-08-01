package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MURIMSCAN", "MurimScan", "en")
internal class MurimScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MURIMSCAN, "murimscan.run", 100) {
	override val withoutAjax = true
	override val postReq = true
}
