package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGABEE", "MangaBee", "en", ContentType.HENTAI)
internal class MangaBee(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGABEE, "mangabee.net") {
	override val datePattern = "MM/dd/yyyy"
}
