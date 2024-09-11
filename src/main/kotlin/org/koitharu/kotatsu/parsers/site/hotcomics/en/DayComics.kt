package org.koitharu.kotatsu.parsers.site.hotcomics.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.hotcomics.HotComicsParser

@MangaSourceParser("DAYCOMICS", "DayComics", "en")
internal class DayComics(context: MangaLoaderContext) :
	HotComicsParser(context, MangaParserSource.DAYCOMICS, "daycomics.me/en")
