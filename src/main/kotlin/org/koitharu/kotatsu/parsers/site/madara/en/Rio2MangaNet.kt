package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RIO2MANGANET", "Rio2Manga.net", "en")
internal class Rio2MangaNet(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RIO2MANGANET, "rio2manga.net", 10)
