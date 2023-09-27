package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TANKOUHENTAI", "Tankou Hentai", "pt", ContentType.HENTAI)
internal class TankouHentai(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TANKOUHENTAI, "tankouhentai.com", pageSize = 16) {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
