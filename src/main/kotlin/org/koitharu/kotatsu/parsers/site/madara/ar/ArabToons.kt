package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ARABTOONS", "ArabToons", "ar", ContentType.HENTAI)
internal class ArabToons(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ARABTOONS, "arabtoons.net") {
	override val datePattern = "dd-MM-yyyy"
}
