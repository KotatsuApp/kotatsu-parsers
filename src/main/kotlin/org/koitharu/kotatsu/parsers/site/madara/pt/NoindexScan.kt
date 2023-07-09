package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NOINDEXSCAN", "Noindex Scan", "pt")
internal class NoindexScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NOINDEXSCAN, "noindexscan.com") {

	override val isNsfwSource = true
	override val datePattern: String = "dd/MM/yyyy"
}
