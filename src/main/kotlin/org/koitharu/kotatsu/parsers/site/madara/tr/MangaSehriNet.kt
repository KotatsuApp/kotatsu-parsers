package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASEHRINET", "MangaSehri.net", "tr")
internal class MangaSehriNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGASEHRINET, "mangasehri.net", 20) {
	override val datePattern = "dd MMMM yyyy"
}
