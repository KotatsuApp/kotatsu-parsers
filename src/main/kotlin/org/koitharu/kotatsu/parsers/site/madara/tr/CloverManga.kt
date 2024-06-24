package org.koitharu.kotatsu.parsers.site.madara.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("CLOVERMANGA", "CloverManga", "tr")
internal class CloverManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.CLOVERMANGA, "clover-manga.com", 20)
