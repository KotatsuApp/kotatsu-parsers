package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANJANOON_EN", "Manjanoon", "en")
internal class Manjanoon(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANJANOON_EN, "manjanoon.net", pageSize = 20, searchPageSize = 10)
