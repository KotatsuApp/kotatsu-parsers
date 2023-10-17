package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASTRASCANS", "AstraScans", "en")
internal class AstraScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ASTRASCANS, "astrascans.com", pageSize = 30, searchPageSize = 10)
