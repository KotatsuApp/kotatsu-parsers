package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("STRAYFANSUB", "StrayFansub", "tr", ContentType.HENTAI)
internal class StrayFansub(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaParserSource.STRAYFANSUB, "strayfansub.com", pageSize = 20, searchPageSize = 10)
