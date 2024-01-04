package org.koitharu.kotatsu.parsers.site.mangareader.es

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("AIYUMANGASCANLATION", "AiyuManhua", "es")
internal class AiyuMangaScanlation(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.AIYUMANGASCANLATION, "www.aiyumanhua.com", 20, 10)
