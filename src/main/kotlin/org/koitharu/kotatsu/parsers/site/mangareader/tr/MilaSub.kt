package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MILASUB", "MilaSub", "tr")
internal class MilaSub(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MILASUB, "www.milasub.com", pageSize = 20, searchPageSize = 10)
