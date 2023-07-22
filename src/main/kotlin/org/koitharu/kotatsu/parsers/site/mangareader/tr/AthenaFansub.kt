package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("ATHENAFANSUB", "AthenaFansub", "tr")
internal class AthenaFansub(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.ATHENAFANSUB, "athenafansub.com", pageSize = 20, searchPageSize = 10)

