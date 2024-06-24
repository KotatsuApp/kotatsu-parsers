package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAMANIACS", "MangaManiacs", "en", ContentType.HENTAI)
internal class MangaManiacs(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAMANIACS, "mangamaniacs.org", 10)
