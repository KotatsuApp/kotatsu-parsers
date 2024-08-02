package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("LUNARSCAN", "LunarScan.com.br", "pt")
internal class LunarScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.LUNARSCAN, "lunarscan.com.br") {
	override val listUrl = "obra/"
}
