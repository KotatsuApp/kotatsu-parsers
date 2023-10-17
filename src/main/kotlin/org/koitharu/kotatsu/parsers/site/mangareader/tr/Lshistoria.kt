package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LSHISTORIA", "LsHistoria", "tr")
internal class Lshistoria(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LSHISTORIA, "lshistoria.com", pageSize = 20, searchPageSize = 10)
