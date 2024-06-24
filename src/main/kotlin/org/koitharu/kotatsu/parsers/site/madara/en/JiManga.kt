package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("JIMANGA", "S2Manga.io", "en")
internal class JiManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.JIMANGA, "s2manga.io")
