package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("APOLL_COMICS", "Apoll Comics", "es")
internal class ApollComics(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.APOLL_COMICS, "apollcomics.com", 10) {

	override val datePattern = "MMMM d, yyyy"
}
