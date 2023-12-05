package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("THEBLANK", "TheBlank", "en", ContentType.HENTAI)
internal class TheBlank(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.THEBLANK, "theblank.net") {
	override val datePattern = "dd/MM/yyyy"
}
