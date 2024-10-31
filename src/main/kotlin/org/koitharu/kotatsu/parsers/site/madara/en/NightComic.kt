package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("NIGHTCOMIC", "Night Comic", "en")
internal class NightComic(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.NIGHTCOMIC, "www.nightcomic.com") {
	override val postReq = true
}
