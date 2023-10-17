package org.koitharu.kotatsu.parsers.site.madara.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("NEKOPOST", "NekoPost", "th")
internal class NekoPost(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.NEKOPOST, "www.nekopost.co") {
	override val postReq = true
	override val datePattern = "d MMMM yyyy"
}
