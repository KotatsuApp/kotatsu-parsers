package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@Broken
@MangaSourceParser("LUFFYMANGA", "LuffyManga", "en")
internal class LuffyManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.LUFFYMANGA, "luffymanga.com", 10)
