package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("AREASCANS", "Areascans", "ar")
internal class Areascans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.AREASCANS, "www.areascans.net", pageSize = 20, searchPageSize = 10)
