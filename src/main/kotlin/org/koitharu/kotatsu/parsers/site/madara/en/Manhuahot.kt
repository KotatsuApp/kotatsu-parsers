package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANHUAHOT", "ManhuaHot", "en")
internal class Manhuahot(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANHUAHOT, "manhuahot.com", 10)
