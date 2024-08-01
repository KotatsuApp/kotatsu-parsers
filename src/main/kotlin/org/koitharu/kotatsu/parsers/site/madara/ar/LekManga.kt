package org.koitharu.kotatsu.parsers.site.madara.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGALEK", "LekManga", "ar")
internal class LekManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGALEK, "lekmanga.net", pageSize = 10)
