package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WONDERLANDSCAN", "Wonderland Scan", "pt")
internal class WonderlandScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WONDERLANDSCAN, "wonderlandscan.com") {

	override val datePattern: String = "dd/MM/yyyy"
}
