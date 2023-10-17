package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MAJORSCANS", "MajorScans", "tr")
internal class MajorScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MAJORSCANS, "www.majorscans.com", pageSize = 20, searchPageSize = 25)
