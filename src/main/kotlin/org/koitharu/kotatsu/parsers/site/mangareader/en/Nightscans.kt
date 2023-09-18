package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("NIGHTSCANS", "Night scans", "en")
internal class Nightscans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.NIGHTSCANS, "nightscans.org", pageSize = 20, searchPageSize = 20) {
	override val selectMangaListImg = "img.ts-post-image, picture img"
}
