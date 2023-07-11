package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser


@MangaSourceParser("INFRAFANDUB", "infrafandub", "es")
internal class Infrafandub(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.INFRAFANDUB, "infrafandub.xyz", 10) {

	override val datePattern = "dd/MM/yyyy"
}
