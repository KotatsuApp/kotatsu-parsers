package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("LUACOMIC_COM", "luaComic.com", "en")
internal class LuaComicCom(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.LUACOMIC_COM, "ponvi.online", pageSize = 20, searchPageSize = 10) {
	override val isTagsExclusionSupported = false
}
