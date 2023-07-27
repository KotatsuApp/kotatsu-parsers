package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("HENTAI20", "Hentai20", "en", ContentType.HENTAI)
internal class Hentai20(context: MangaLoaderContext) : MadaraParser(context, MangaSource.HENTAI20, "hentai20.io") {

	override val datePattern = "MMMM dd, yyyy"
}
