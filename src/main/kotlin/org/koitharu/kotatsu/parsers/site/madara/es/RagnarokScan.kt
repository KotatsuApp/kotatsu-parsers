package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RAGNAROKSCAN", "Ragnarok Scan", "es")
internal class RagnarokScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RAGNAROKSCAN, "ragnarokscan.com") {

	override val stylepage = ""
	override val tagPrefix = "genero/"
	override val datePattern = "MMMM d, yyyy"

}
