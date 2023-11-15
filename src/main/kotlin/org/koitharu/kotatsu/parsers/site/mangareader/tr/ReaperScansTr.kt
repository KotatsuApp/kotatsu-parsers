package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("REAPERSCANSTR", "ReaperScansTr", "tr")
internal class ReaperScansTr(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.REAPERSCANSTR, "reaperscans.com.tr", pageSize = 20, searchPageSize = 10)
