package org.koitharu.kotatsu.parsers.site.madara.vi

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser
import org.koitharu.kotatsu.parsers.Broken

@Broken("No longer available")
@MangaSourceParser("FECOMICC", "Fecomic", "vi")
internal class Fecomicc(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.FECOMICC, "mangasup.net", 9) {
	override val listUrl = "comic/"
	override val tagPrefix = "the-loai/"
	override val datePattern = "dd/MM/yyyy"
}
