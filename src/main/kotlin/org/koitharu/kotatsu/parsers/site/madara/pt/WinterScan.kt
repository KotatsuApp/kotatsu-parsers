package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("WINTERSCAN", "Winter Scan", "pt")
internal class WinterScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.WINTERSCAN, "winterscan.com", pageSize = 20) {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
