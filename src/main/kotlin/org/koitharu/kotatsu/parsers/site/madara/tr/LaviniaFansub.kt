package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("LAVINIAFANSUB", "LaviniaFansub", "tr", ContentType.HENTAI)
internal class LaviniaFansub(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.LAVINIAFANSUB, "laviniafansub.com", 18) {
	override val datePattern = "dd/MM/yyyy"
}
