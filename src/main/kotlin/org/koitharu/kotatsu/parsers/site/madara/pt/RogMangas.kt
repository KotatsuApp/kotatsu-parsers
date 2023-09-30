package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ROGMANGAS", "Rog Mangas", "pt")
internal class RogMangas(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ROGMANGAS, "rogmangas.com", 51) {
	override val datePattern: String = "dd/MM/yyyy"
}
