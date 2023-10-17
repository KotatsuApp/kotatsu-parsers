package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("REMANGAS", "ReMangas", "pt")
internal class ReMangas(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.REMANGAS, "remangas.net") {
	override val datePattern = "dd/MM/yyyy"
}
