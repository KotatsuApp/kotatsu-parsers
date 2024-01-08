package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MAFIAMANGA", "MafiaManga", "th")
internal class MafiaManga(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MAFIAMANGA, "mafia-manga.com", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
