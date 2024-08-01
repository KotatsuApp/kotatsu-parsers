package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("SHADOWXMANGA", "ShadowXManga", "ar")
internal class ShadowxManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.SHADOWXMANGA, "www.shadowxmanga.com") {
	override val datePattern = "yyyy/MM/dd"
}
