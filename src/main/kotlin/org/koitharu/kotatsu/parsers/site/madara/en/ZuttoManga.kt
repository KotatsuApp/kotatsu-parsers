package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("ZUTTOMANGA", "Zutto Manga", "en")
internal class ZuttoManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.ZUTTOMANGA, "zuttomanga.com") {
	override val postreq = true
}
