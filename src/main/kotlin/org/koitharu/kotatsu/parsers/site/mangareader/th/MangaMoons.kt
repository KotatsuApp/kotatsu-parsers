package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGAMOONS", "Manga Moons", "th")
internal class MangaMoons(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGAMOONS, "manga-moons.net", pageSize = 20, searchPageSize = 10)
