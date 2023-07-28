package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser


@MangaSourceParser("LYNXSCANS", "LynxScans", "en")
internal class LynxScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.LYNXSCANS, "lynxscans.com", pageSize = 25, searchPageSize = 10) {

	override val listUrl = "/comics"

}
