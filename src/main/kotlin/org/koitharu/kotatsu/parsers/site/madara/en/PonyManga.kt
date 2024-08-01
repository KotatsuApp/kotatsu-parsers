package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("PONYMANGA", "PonyManga", "en", ContentType.HENTAI)
internal class PonyManga(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.PONYMANGA, "ponymanga.com", 10)
