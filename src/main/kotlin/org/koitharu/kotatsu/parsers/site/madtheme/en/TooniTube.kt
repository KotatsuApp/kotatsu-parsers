package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser

@MangaSourceParser("TOONITUBE", "TooniTube", "en", ContentType.HENTAI)
internal class TooniTube(context: MangaLoaderContext) :
	MadthemeParser(context, MangaParserSource.TOONITUBE, "toonitube.com") {
	override val selectDesc = "div.summary div.section-body p.content"
}
