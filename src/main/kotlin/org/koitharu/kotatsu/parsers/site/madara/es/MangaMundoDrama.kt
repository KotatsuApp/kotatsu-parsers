package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAMUNDODRAMA", "Manga Mundo Drama", "es")
internal class MangaMundoDrama(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAMUNDODRAMA, "manga.mundodrama.site") {
	override val listUrl = "mg/"
}
