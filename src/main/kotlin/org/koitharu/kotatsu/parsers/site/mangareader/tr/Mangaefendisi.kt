package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAEFENDISI", "MangaEfendisi", "tr")
internal class Mangaefendisi(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAEFENDISI, "mangaefendisi.net", pageSize = 30, searchPageSize = 20)
