package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@Broken
@MangaSourceParser("MANHWA_FREAK", "ManhwaFreak", "en")
internal class ManhwaFreak(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.MANHWA_FREAK, "manhwafreak.xyz", pageSize = 30, searchPageSize = 42)
