package org.koitharu.kotatsu.parsers.site.foolslide.en

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.site.foolslide.FoolSlideParser

@MangaSourceParser("DEATHTOLLSCANS", "DeathTollScans", "en")
internal class Deathtollscans(context: MangaLoaderContext) :
	FoolSlideParser(context, MangaParserSource.DEATHTOLLSCANS, "reader.deathtollscans.net", 26)
