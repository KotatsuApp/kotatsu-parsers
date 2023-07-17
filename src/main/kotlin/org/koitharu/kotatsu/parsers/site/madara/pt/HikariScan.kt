package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HIKARISCAN", "Hikari Scan", "pt")
internal class HikariScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HIKARISCAN, "hikariscan.com.br", pageSize = 10) {

	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
