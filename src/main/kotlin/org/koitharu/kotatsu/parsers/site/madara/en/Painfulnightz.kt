package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PAINFULNIGHTZ", "Painfulnightz", "en")
internal class Painfulnightz(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.PAINFULNIGHTZ, "painfulnightz.com") {

	override val datePattern = "d MMMM"
}
