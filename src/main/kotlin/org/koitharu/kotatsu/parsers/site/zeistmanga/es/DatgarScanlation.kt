package org.koitharu.kotatsu.parsers.site.zeistmanga.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.zeistmanga.ZeistMangaParser

@MangaSourceParser("DATGARSCANLATION", "DatgarScanlation", "es")
internal class DatgarScanlation(context: MangaLoaderContext) :
	ZeistMangaParser(context, MangaParserSource.DATGARSCANLATION, "datgarscanlation.blogspot.com")
