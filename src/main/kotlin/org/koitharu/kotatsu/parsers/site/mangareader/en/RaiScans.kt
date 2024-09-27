package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RAISCANS", "KenScans", "en")
internal class RaiScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.RAISCANS, "kenscans.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
}
