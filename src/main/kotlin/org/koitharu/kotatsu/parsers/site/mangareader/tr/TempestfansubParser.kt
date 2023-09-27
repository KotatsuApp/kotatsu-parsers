package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("TEMPESTFANSUB", "Tempest Fansub", "tr")
internal class TempestfansubParser(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TEMPESTFANSUB, "tempestfansub.com", pageSize = 25, searchPageSize = 40)
