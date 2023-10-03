package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SURYASCANS", "Surya Scans", "en")
internal class SuryaScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SURYASCANS, "suryareader.com", pageSize = 5, searchPageSize = 5)
