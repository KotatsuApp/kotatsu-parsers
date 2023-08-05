package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("PEACESCANS", "PeaceScans", "en")
internal class PeaceScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.PEACESCANS, "manhwax.org", pageSize = 12, searchPageSize = 10)
