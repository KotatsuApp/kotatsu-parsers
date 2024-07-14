package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

//This source requires an account.
@MangaSourceParser("GRIMELEK", "Grimelek", "tr")
internal class Grimelek(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GRIMELEK, "glorymanga.com", 20) {
	override val datePattern = "d MMMM yyyy"
	override val listUrl = "seri/"
}
