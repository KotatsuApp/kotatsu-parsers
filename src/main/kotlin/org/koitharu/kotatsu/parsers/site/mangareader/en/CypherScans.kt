package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("CYPHERSCANS", "Cypher Scans", "en")
internal class CypherScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.CYPHERSCANS, "cypherscans.xyz", pageSize = 20, searchPageSize = 10)
