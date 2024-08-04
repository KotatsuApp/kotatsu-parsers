package org.koitharu.kotatsu.parsers.site.hotcomics.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.hotcomics.HotComicsParser

@MangaSourceParser("HOTCOMICS", "HotComics", "en")
internal class HotComics(context: MangaLoaderContext) :
	HotComicsParser(context, MangaParserSource.HOTCOMICS, "hotcomics.me/en")
