package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

// Redirect to luacomic.com
@MangaSourceParser("KAISCANS", "KaiScans", "en")
internal class KaiScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.KAISCANS, "luacomic.com", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
