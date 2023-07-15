package org.koitharu.kotatsu.parsers.site.madara.en


import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("RIO2MANGA", "Rio2Manga", "en")
internal class Rio2Manga(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.RIO2MANGA, "rio2manga.com", 10)
