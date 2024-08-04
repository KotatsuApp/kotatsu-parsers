package org.koitharu.kotatsu.parsers.site.iken.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.iken.IkenParser

@MangaSourceParser("MANGAGALAXY", "MangaGalaxy", "en")
internal class MangaGalaxyParser(context: MangaLoaderContext) :
	IkenParser(context, MangaParserSource.MANGAGALAXY, "mangagalaxy.org")
