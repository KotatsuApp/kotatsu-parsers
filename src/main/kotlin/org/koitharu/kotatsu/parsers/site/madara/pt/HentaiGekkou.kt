package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAIGEKKOU", "Hentai Gekkou", "pt", ContentType.HENTAI)
internal class HentaiGekkou(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAIGEKKOU, "hentai.gekkouscans.com.br", 10) {

	override val tagPrefix = "genero/"
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
