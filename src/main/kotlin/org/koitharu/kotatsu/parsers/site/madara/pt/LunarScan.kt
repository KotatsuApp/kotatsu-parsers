package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LUNARSCAN", "LunarScan", "pt")
internal class LunarScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LUNARSCAN, "lunarscan.com.br") {
	override val listUrl = "obra/"
}
