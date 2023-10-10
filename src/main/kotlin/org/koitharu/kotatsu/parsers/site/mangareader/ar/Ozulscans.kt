package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("OZULSCANS", "Ozul Scans", "ar")
internal class Ozulscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.OZULSCANS, "ar-ozulscans.com", pageSize = 30, searchPageSize = 30)
