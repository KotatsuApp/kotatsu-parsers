package org.koitharu.kotatsu.parsers.site.mangareader.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("DRAKESCANS", "DrakeComic", "en")
internal class DrakeScans(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.DRAKESCANS, "drakecomic.org", 20, 10)
