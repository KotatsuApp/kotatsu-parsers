package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RACKUSREADS", "RackusReads", "en")
internal class RackusReads(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.RACKUSREADS, "rackusreads.com", 20) {
	override val datePattern = "MM/dd/yyyy"
}
