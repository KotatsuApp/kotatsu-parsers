package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NOVELSTOWN", "Novels Town", "ar")
internal class Novelstown(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NOVELSTOWN, "novelstown.com") {
	override val datePattern = "d MMMM، yyyy"
}
