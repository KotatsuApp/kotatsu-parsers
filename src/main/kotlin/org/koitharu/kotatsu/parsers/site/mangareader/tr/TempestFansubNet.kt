package org.koitharu.kotatsu.parsers.site.mangareader.tr

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("TEMPESTFANSUBNET", "TempestFansub.net", "tr")
internal class TempestFansubNet(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.TEMPESTFANSUBNET, "tempestfansub.net", pageSize = 30, searchPageSize = 10)
