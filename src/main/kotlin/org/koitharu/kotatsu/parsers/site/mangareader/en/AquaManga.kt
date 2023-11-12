package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("AQUAMANGA_LIVE", "AquaManga.live", "en")
internal class AquaManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.AQUAMANGA_LIVE, "aquamanga.live", pageSize = 30, searchPageSize = 10)
