package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("MANGATX_TO", "MangaTx.to", "en")
internal class MangaTxTo(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGATX_TO, "mangatx.to", 10) {
	override val tagPrefix = "manhua-genre/"
}
