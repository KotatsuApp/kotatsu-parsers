package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import java.util.Locale


@MangaSourceParser("HENTAIGEKKOU", "Hentai Gekkou", "pt")
internal class HentaiGekkou(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HENTAIGEKKOU, "hentai.gekkouscans.com.br", 10) {

	override val tagPrefix = "genero/"
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
	override val sourceLocale: Locale = Locale("pt", "PT")
	override val isNsfwSource = true
}
