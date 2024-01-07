package org.koitharu.kotatsu.parsers.site.mangareader.th

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("SODSAIME", "สดใสเมะ", "th")
internal class Sodsaime(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.SODSAIME, "www.xn--l3c0azab5a2gta.com", pageSize = 40, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
