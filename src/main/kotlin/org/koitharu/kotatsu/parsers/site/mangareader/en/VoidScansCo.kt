package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.Broken
import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@Broken
@MangaSourceParser("VOIDSCANS_CO", "VoidScans", "en")
internal class VoidScansCo(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.VOIDSCANS_CO, "voidscans.co", pageSize = 30, searchPageSize = 42)
