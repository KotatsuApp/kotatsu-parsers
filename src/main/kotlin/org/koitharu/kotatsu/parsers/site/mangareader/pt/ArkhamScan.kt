package org.koitharu.kotatsu.parsers.site.mangareader.pt

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ARKHAMSCAN", "ArkhamScan", "pt")
internal class ArkhamScan(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ARKHAMSCAN, "arkhamscan.com", pageSize = 20, searchPageSize = 10)
