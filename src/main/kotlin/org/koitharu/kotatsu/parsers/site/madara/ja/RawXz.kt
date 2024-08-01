package org.koitharu.kotatsu.parsers.site.madara.ja

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RAWXZ", "RawXz", "ja")
internal class RawXz(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.RAWXZ, "rawxz.si") {
	override val listUrl = "jp-manga/"
}
