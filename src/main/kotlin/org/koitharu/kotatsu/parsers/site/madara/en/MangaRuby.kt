package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGARUBY", "MangaRuby", "en")
internal class MangaRuby(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGARUBY, "mangaruby.com", 10)
