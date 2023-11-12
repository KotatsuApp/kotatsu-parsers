package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HADESNOFANSUB", "HadesNoFansub", "es")
internal class HadesNoFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.HADESNOFANSUB, "hadesnofansub.com", 10) {
	override val datePattern: String = "MM/dd/yyyy"
}
