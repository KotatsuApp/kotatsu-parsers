package org.koitharu.kotatsu.parsers.site.madara.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("APOLL_COMICS", "ApollComics", "es")
internal class ApollComics(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.APOLL_COMICS, "apollcomics.es", 10)
