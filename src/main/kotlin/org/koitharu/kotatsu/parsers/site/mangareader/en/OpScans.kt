package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("OPSCANS", "OpScanlations", "en")
internal class OpScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.OPSCANS, "opscanlations.com", pageSize = 20, searchPageSize = 10)
