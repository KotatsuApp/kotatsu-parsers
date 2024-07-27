package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("NOVELSTOWN", "NovelsTown", "ar")
internal class Novelstown(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.NOVELSTOWN, "novelstown.com") {
	override val datePattern = "d MMMM، yyyy"
}
