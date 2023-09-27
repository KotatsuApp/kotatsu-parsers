package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("TURKTOON", "Turk Toon", "tr")
internal class TurktoonParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TURKTOON, "turktoon.com", pageSize = 20, searchPageSize = 10)
