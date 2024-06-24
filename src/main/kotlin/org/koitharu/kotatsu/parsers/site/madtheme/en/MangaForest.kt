package org.koitharu.kotatsu.parsers.site.madtheme.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madtheme.MadthemeParser

@MangaSourceParser("MANGAFOREST", "MangaForest", "en")
internal class MangaForest(context: MangaLoaderContext) :
	MadthemeParser(context, MangaParserSource.MANGAFOREST, "mangaforest.me")
