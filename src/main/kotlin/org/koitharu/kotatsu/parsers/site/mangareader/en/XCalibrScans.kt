package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("XCALIBRSCANS", "XCalibr Scans", "en")
internal class XCalibrScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.XCALIBRSCANS, "xcalibrscans.com", pageSize = 20, searchPageSize = 10)
