package org.koitharu.kotatsu.parsers.site.madara.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("YAOITOSHOKAN", "YaoiToShokan", "pt", ContentType.HENTAI)
internal class Yaoitoshokan(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.YAOITOSHOKAN, "www.yaoitoshokan.net", 18) {
	override val tagPrefix = "genero/"
	override val datePattern: String = "d MMM yyyy"
}
