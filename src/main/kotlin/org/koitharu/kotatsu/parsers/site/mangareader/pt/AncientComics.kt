package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ANCIENTCOMICS", "AncientComics", "pt")
internal class AncientComics(context: MangaLoaderContext) :
	MangaReaderParser(
		context,
		MangaParserSource.ANCIENTCOMICS,
		"ancientcomics.com.br",
		pageSize = 20,
		searchPageSize = 20,
	)
