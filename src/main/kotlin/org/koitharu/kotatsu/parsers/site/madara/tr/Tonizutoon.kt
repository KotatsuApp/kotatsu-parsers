package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("TONIZUTOON", "ToniZu.com", "tr", ContentType.HENTAI)
internal class Tonizutoon(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.TONIZUTOON, "tonizu.com") {
	override val datePattern = "dd/mm/yyyy"
}
