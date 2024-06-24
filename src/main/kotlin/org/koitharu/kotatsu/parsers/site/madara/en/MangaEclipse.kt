package org.koitharu.kotatsu.parsers.site.madara.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.madara.MadaraParser

@MangaSourceParser("MANGAECLIPSE", "MangaEclipse", "en")
internal class MangaEclipse(context: MangaLoaderContext) :
	MadaraParser(context, MangaParserSource.MANGAECLIPSE, "mangaeclipse.com")
