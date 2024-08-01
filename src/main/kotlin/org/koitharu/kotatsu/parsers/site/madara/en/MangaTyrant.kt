package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGATYRANT", "MangaTyrant", "en")
internal class MangaTyrant(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGATYRANT, "mangatyrant.com")
