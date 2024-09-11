package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken // Redirect to @MortalsGroove
@MangaSourceParser("IMMORTALUPDATES", "ImmortalUpdates", "en")
internal class ImmortalUpdates(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.IMMORTALUPDATES, "immortalupdates.com") {
	override val listUrl = "mangas/"
}
