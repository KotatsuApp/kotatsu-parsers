package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("KINGOFSHOJO", "KingOfShojo", "ar")
internal class KingOfShojo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.KINGOFSHOJO, "kingofshojo.com", pageSize = 30, searchPageSize = 10)
