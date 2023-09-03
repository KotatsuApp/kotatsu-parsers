package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GHOSTSCAN", "GhostScan", "pt")
internal class GhostScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GHOSTSCAN, "ghostscan.com.br", 24) {

	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
