package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MANGASHIINA", "MangaShiina", "es")
internal class MangaShiina(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MANGASHIINA, "mangashiina.com", pageSize = 20, searchPageSize = 10)
