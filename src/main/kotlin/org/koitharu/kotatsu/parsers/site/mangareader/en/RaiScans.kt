package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("RAISCANS", "RaiScans", "en")
internal class RaiScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.RAISCANS, "www.raiscans.com", pageSize = 20, searchPageSize = 10) {
	override val listUrl = "/Series"
}
