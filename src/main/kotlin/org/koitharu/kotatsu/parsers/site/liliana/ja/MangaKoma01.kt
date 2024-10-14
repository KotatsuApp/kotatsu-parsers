package org.koitharu.kotatsu.parsers.site.liliana.ja

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.liliana.LilianaParser

@MangaSourceParser("MANGAKOMA01", "MangaKoma01", "ja")
internal class MangaKoma01(context: MangaLoaderContext) :
	LilianaParser(context, MangaParserSource.MANGAKOMA01, "mangakoma01.net")
