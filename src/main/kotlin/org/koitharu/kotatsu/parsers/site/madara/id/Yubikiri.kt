package org.koitharu.kotatsu.parsers.site.madara.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YUBIKIRI", "Yubikiri", "id")
internal class Yubikiri(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.YUBIKIRI, "yubikiri.my.id", 18) {
	override val tagPrefix = "genre/"
	override val datePattern = "d MMMM"
}
