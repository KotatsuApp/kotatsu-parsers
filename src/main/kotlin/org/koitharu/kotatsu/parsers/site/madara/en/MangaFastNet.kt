package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAFASTNET", "MangaFast.net", "en")
internal class MangaFastNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAFASTNET, "manhuafast.net") {
	override val withoutAjax = true

}
