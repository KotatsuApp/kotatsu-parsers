package org.koitharu.kotatsu.parsers.site.madara.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("EPSILONSCAN", "EpsilonScan", "fr", ContentType.HENTAI)
internal class EpsilonscanParser(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.EPSILONSCAN, "epsilonscan.to") {
	override val datePattern = "dd/MM/yy"
	override val withoutAjax = true
}
