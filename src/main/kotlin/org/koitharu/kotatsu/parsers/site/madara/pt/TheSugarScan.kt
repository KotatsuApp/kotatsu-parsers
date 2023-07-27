package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("THESUGARSCAN", "The Sugar Scan", "pt", ContentType.HENTAI)
internal class TheSugarScan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.THESUGARSCAN, "thesugarscan.com", pageSize = 15) {

	override val datePattern: String = "dd/MM/yyyy"
}
