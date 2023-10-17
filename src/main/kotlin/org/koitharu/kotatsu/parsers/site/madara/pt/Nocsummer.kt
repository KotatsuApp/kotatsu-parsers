package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NOCSUMMER", "NocturneSummer", "pt")
internal class Nocsummer(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NOCSUMMER, "nocsummer.com.br", 18) {
	override val datePattern = "dd 'de' MMMMM 'de' yyyy"
}
