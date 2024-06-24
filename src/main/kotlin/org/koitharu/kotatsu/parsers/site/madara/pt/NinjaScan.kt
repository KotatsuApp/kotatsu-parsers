package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NINJASCAN", "NinjaScan", "pt")
internal class NinjaScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.NINJASCAN, "ninjascan.site") {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
