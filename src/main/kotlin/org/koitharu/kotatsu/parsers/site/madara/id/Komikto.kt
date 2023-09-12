package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("KOMIKTO", "Komikto", "id")
internal class Komikto(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.KOMIKTO, "komikto.com", 10) {

	override val tagPrefix = "grafis/"
	override val listUrl = "comic/"
}
