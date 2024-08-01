package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGASUSHI", "MangaSushi", "en")
internal class MangaSushi(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGASUSHI, "mangasushi.org")
