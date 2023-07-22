package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("GALAXYMANGA", "Galaxymanga", "ar")
internal class Galaxymanga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.GALAXYMANGA, "galaxymanga.org", pageSize = 40, searchPageSize = 30)
