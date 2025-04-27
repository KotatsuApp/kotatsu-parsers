package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("BOOKMANGA", "BookManga", "en")
internal class BookManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.BOOKMANGA, "bookmanga.com", 20)
