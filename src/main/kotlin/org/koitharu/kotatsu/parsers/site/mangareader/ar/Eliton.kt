package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ELITON", "ThunderScans", "ar")
internal class Eliton(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ELITON, "thunderscans.com", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
