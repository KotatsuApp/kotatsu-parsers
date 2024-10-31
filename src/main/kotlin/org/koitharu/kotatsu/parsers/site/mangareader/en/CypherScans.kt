package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("CYPHERSCANS", "CypherScans", "en")
internal class CypherScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.CYPHERSCANS, "cyphscans.xyz", pageSize = 20, searchPageSize = 10)
