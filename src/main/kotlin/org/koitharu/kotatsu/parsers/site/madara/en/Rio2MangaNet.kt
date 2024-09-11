package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RIO2MANGANET", "ZinchanManga.mobi", "en")
internal class Rio2MangaNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.RIO2MANGANET, "zinchanmanga.mobi", 10)
