package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ASEMIFANSUB", "AsemiFansub", "tr")
internal class AsemiFansub(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.ASEMIFANSUB, "asemifansub.com", pageSize = 20, searchPageSize = 10)
