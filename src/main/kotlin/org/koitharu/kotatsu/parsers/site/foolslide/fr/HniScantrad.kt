package org.koitharu.kotatsu.parsers.site.foolslide.fr

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser

@Broken // Not dead, changed template
@MangaSourceParser("HNISCANTRAD", "HniScantrad", "fr")
internal class HniScantrad(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaParserSource.HNISCANTRAD, "hni-scantrad.net") {

	override val pagination = false
	override val searchUrl = "lel/search/"
	override val listUrl = "lel/directory/"
}
