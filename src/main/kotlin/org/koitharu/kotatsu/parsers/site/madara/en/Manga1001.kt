package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGA1001", "Manga 1001", "en")
internal class Manga1001(context: MangaLoaderContext) :
	MadaraParser(context, MangaSource.MANGA1001, "manga-1001.com", 10)
