package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("EMPERORSCAN", "Emperor Scan", "es")
internal class EmperorScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.EMPERORSCAN, "dokkomanga.com") {

	override val datePattern = "MMMM d, yyyy"
}
