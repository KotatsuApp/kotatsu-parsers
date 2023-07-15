package org.koitharu.kotatsu.parsers.site.madara.pt


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAREADING", "MangaReading", "en")
internal class MangaReading(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGAREADING, "mangareading.org") {

	override val datePattern = "dd.MM.yyyy"
}
