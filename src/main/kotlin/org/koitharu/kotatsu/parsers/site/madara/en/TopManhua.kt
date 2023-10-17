package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TOPMANHUA", "TopManhua", "en")
internal class TopManhua(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TOPMANHUA, "www.topmanhua.com") {
	override val tagPrefix = "manhua-genre/"
	override val datePattern = "MM/dd/yyyy"
}
