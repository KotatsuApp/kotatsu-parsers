package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("BEASTSCANS", "Beast Scans", "ar")
internal class BeastScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.BEASTSCANS, "beastscans.net", pageSize = 20, searchPageSize = 10)
