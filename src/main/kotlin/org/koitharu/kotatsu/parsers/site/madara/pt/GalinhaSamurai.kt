package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GALINHASAMURAI", "Galinha Samurai", "pt")
internal class GalinhaSamurai(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GALINHASAMURAI, "galinhasamurai.com") {
	override val datePattern = "dd/MM/yyyy"
}
