package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHWASCO", "Manhwasco", "en")
internal class Manhwasco(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANHWASCO, "manhwasco.net") {

	override val selectgenre = "div.mg_genres a"

}
