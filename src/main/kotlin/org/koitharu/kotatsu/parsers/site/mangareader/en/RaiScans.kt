package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RAISCANS", "RaiScans", "en")
internal class RaiScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.RAISCANS, "kenmanga.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/series"
}
