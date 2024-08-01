package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DRAGONMANGA", "DragonManga", "th", ContentType.HENTAI)
internal class DragonManga(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.DRAGONMANGA,
		"www.dragon-manga.com",
		pageSize = 40,
		searchPageSize = 10,
	)
