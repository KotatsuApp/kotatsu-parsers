package org.koitharu.kotatsu.parsers.site.mangareader.fr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RIMUSCANS", "RimuScans", "fr")
internal class RimuScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.RIMUSCANS, "rimuscans.fr", pageSize = 30, searchPageSize = 10)
