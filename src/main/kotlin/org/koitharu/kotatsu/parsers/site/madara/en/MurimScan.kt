package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MURIMSCAN", "Murim Scan", "en")
internal class MurimScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MURIMSCAN, "murimscan.run", 100) {
	override val withoutAjax = true
	override val postreq = true
}
