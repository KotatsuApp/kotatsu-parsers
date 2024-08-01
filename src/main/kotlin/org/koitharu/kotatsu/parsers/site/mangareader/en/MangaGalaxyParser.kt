package org.koitharu.kotatsu.parsers.site.mangareader.en


import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@Broken // Not dead, changed template
@MangaSourceParser("MANGAGALAXY", "MangaGalaxy", "en")
internal class MangaGalaxyParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANGAGALAXY, "mangagalaxy.org", 20, 10)
