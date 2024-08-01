package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("MANHUASY", "ManhuaSy", "en")
internal class Manhuasy(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHUASY, "www.manhuasy.com") {
	override val listUrl = "manhua/"
	override val tagPrefix = "manhua-genre/"
}
