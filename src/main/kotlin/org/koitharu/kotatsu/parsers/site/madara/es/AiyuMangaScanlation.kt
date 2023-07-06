package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("AIYUMANGASCANLATION", "AiyuMangaScanlation", "es")
internal class AiyuMangaScanlation(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.AIYUMANGASCANLATION, "aiyumangascanlation.com") {

	override val datePattern = "MM/dd/yyyy"
}
