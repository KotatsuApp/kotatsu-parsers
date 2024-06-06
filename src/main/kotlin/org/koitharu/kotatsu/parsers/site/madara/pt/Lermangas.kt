package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LERMANGAS", "Lermangas", "pt")
internal class Lermangas(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.LERMANGAS, "lermangas.me", pageSize = 20) {
	override val datePattern = "dd 'de' MMMMM 'de' yyyy"
}
