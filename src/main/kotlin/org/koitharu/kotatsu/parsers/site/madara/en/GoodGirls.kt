package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("GOODGIRLS", "Good Girls", "en")
internal class GoodGirls(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.GOODGIRLS, "goodgirls.moe", 10) {
	override val selectDesc = "div.post-content_item:contains(Synopsis) div.summary-content"
}
