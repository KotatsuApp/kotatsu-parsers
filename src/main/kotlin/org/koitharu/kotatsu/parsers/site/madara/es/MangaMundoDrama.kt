package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAMUNDODRAMA", "MangaMundoDrama", "es")
internal class MangaMundoDrama(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAMUNDODRAMA, "manga.mundodrama.site") {

	override val listUrl = "mg/"
}
