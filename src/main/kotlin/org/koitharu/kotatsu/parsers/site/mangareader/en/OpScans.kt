package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("OPSCANS", "OpScans", "en")
internal class OpScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.OPSCANS, "opscans.com", pageSize = 20, searchPageSize = 10)
