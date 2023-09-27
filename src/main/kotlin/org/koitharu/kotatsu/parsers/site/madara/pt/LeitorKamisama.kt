package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LEITORKAMISAMA", "Leitor Kamisama", "pt")
internal class LeitorKamisama(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LEITORKAMISAMA, "leitor.kamisama.com.br", 10) {
	override val datePattern: String = "dd 'de' MMMMM 'de' yyyy"
}
