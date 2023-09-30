package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("QUEENSCANS", "Queen Scans", "en")
internal class QueenScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.QUEENSCANS, "fairymanga.com", pageSize = 20, searchPageSize = 10)
