package org.koitharu.kotatsu.parsers.site.mangareader.ar

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ARAREASCANS", "ArAreaScans", "ar")
internal class ArAreaScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ARAREASCANS, "ar.areascans.org", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
